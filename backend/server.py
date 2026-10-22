"""Local voice token broker. Optional home-LAN binding; no audio stored."""
import argparse
import collections
import json
import ipaddress
import os
from pathlib import Path
import socket
import threading
import time
import urllib.error
import urllib.request
from urllib.parse import urlsplit, parse_qs
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from answer_gateway import parse_answer

ENV_FILE = Path(__file__).with_name('.env')
VERSION_FILE = Path(__file__).with_name('VERSION')

def service_version(path=VERSION_FILE):
    """One version string, shared with backend/VERSION. Not a hard-coded literal."""
    return path.read_text(encoding='utf-8').strip()
XAI_ENDPOINT = 'https://api.x.ai/v1/realtime/client_secrets'


def read_key(path=ENV_FILE):
    """Re-read on each session so editing .env does not require a restart."""
    key = os.environ.get('XAI_API_KEY', '').strip()
    if key:
        return key
    if path.exists():
        for line in path.read_text(encoding='utf-8-sig').splitlines():
            name, sep, value = line.partition('=')
            if sep and name.strip() == 'XAI_API_KEY':
                return value.strip().strip('\"\'')
    return ''


def mint_token(key):
    request = urllib.request.Request(
        XAI_ENDPOINT, data=json.dumps({'expires_after': {'seconds': 300}}).encode(),
        headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'},
        method='POST')
    with urllib.request.urlopen(request, timeout=20) as response:
        data = json.loads(response.read(65536))
    if not isinstance(data.get('value'), str) or not data['value']:
        raise ValueError('Invalid token response')
    # Return only the documented client credentials, never the permanent key.
    return {'value': data['value'], 'expires_at': data.get('expires_at', 0)}


class TokenServer(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, handler=None, lan_cidr=None, enable_bots=False, disable_xai=False, tailscale_host=None, only_client=None):
        self.only_client = ipaddress.ip_address(only_client) if only_client else None
        if tailscale_host and (address[0] != '127.0.0.1' or not tailscale_host.endswith('.ts.net') or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789.-' for c in tailscale_host)):
            raise ValueError('Tailscale proxy requires loopback and an exact ts.net hostname')
        self.tailscale_host = tailscale_host
        self.xai_enabled = not disable_xai
        self.bot_gateway = None
        self.activity_gateway = None
        self.answer_gateway = None
        from app_updates import UpdateStore
        self.update_store=UpdateStore()
        if enable_bots:
            from bot_gateway import BotGateway
            self.bot_gateway = BotGateway()
            from activity_gateway import ActivityGateway
            self.activity_gateway = ActivityGateway(self.bot_gateway.roster)
            from answer_gateway import ANSWER_STORE, AnswerGateway
            self.answer_gateway = AnswerGateway(self.activity_gateway, store_path=ANSWER_STORE)
        self.lan = ipaddress.ip_network(lan_cidr) if lan_cidr else None
        private_blocks = [ipaddress.ip_network(cidr) for cidr in ('10.0.0.0/8','172.16.0.0/12','192.168.0.0/16')]
        if self.lan and not any(self.lan.subnet_of(block) for block in private_blocks):
            raise ValueError('LAN subnet must be an RFC1918 home network')
        bind = ipaddress.ip_address(address[0])
        if address[0] != '127.0.0.1' and (not self.lan or bind not in self.lan):
            raise ValueError('Choose loopback or an explicitly configured LAN address')
        super().__init__(address, handler or Handler)
        self.sessions = collections.deque()
        self.rate_lock = threading.Lock()
        self.started_at = int(time.time())

    def allow_session(self):
        with self.rate_lock:
            now = time.monotonic()
            while self.sessions and self.sessions[0] < now - 60:
                self.sessions.popleft()
            if len(self.sessions) >= 6:
                return False
            self.sessions.append(now)
            return True


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # No headers, credentials, request bodies, or transcripts in logs.

    def reply(self, status, data):
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        self.wfile.write(body)

    def local_request(self):
        # Reject browser-origin requests and DNS rebinding. There is no CORS.
        host = self.headers.get('Host', '').split(':')[0]
        client = ipaddress.ip_address(self.client_address[0])
        allowed_host = host in ('127.0.0.1', 'localhost', '10.0.2.2')
        # Tailscale Serve terminates private HTTPS and proxies locally. Accept
        # only its explicitly configured hostname, and only from loopback.
        if client.is_loopback and self.server.tailscale_host:
            allowed_host = allowed_host or host == self.server.tailscale_host
        if self.server.lan:
            try:
                allowed_host = allowed_host or ipaddress.ip_address(host) in self.server.lan
            except ValueError:
                pass
        allowed_client = client.is_loopback or bool(self.server.lan and client in self.server.lan)
        if self.server.only_client and not client.is_loopback:
            allowed_client = client == self.server.only_client
        return allowed_client and allowed_host and not any(
            self.headers.get(h) for h in ('Origin', 'Referer', 'Sec-Fetch-Site'))

    def do_GET(self):
        if not self.local_request():
            return self.reply(403, {'error': 'local_clients_only'})
        route=urlsplit(self.path)
        if route.path in ('/app/update','/app/update.apk'):
            try:manifest,apk=self.server.update_store.load()
            except (OSError,ValueError,KeyError,TypeError):return self.reply(503,{'error':'update_unavailable'})
            if route.path=='/app/update':return self.reply(200,manifest)
            if parse_qs(route.query).get('sha256')!=[manifest['sha256']]:return self.reply(409,{'error':'update_changed_check_again'})
            self.send_response(200);self.send_header('Content-Type','application/vnd.android.package-archive');self.send_header('Content-Length',str(len(apk)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.end_headers();self.wfile.write(apk);return
        if self.server.activity_gateway and urlsplit(self.path).path == '/bot/activity':
            try:
                values=parse_qs(urlsplit(self.path).query).get('bot',[])
                if len(values)!=1:raise ValueError('choose_configured_bot')
                snapshot=self.server.activity_gateway.get(values[0])
                if parse_qs(route.query).get('summary')==['1']:
                    from activity_gateway import activity_summary
                    snapshot=activity_summary(snapshot)
                return self.reply(200,snapshot)
            except ValueError:
                return self.reply(400,{'error':'choose_configured_bot'})
        if self.server.bot_gateway and self.path == '/bots':
            try:
                return self.reply(200, {'bots': self.server.bot_gateway.roster()})
            except (OSError, ValueError, KeyError):
                return self.reply(503, {'error': 'bot_roster_unavailable'})
        if self.server.bot_gateway and self.path.startswith('/bot/jobs/'):
            job = self.server.bot_gateway.get(self.path.removeprefix('/bot/jobs/'))
            return self.reply(200 if job else 404, job or {'error': 'job_not_found'})
        if self.path != '/health':
            return self.reply(404, {'error': 'not_found'})
        self.reply(200, {'service': 'grok-home-voice', 'key_configured': self.server.xai_enabled and bool(read_key()), 'xai_enabled': self.server.xai_enabled, 'bots_enabled': bool(self.server.bot_gateway), 'activity_enabled': bool(self.server.activity_gateway), 'updates_enabled':True,'team_stage_enabled':bool(self.server.bot_gateway),'raise_hand_enabled':bool(self.server.activity_gateway), 'version':service_version(), 'started_at':self.server.started_at})

    def do_POST(self):
        if not self.local_request():
            return self.reply(403, {'error': 'local_clients_only'})
        if self.server.answer_gateway and self.path == '/bot/answer':
            if self.headers.get('Content-Type','').split(';')[0]!='application/json':return self.reply(415,{'error':'json_required'})
            try:
                size=int(self.headers.get('Content-Length','-1'))
                if not 0<=size<=4000:return self.reply(413,{'error':'invalid_length'})
                data=json.loads(self.rfile.read(size))
                bot,needs_you_id,option_id,key=parse_answer(data)
                return self.reply(200,self.server.answer_gateway.submit(bot,needs_you_id,option_id,key))
            except ValueError as error:
                code=str(error)
                return self.reply(400,{'error':code if code in ('choose_configured_bot','invalid_request','invalid_idempotency_key') else 'invalid_request'})
        if self.server.bot_gateway and self.path == '/bot/cancel':
            if self.headers.get('Content-Type','').split(';')[0]!='application/json':return self.reply(415,{'error':'json_required'})
            try:
                size=int(self.headers.get('Content-Length','-1'))
                if not 0<=size<=2000:return self.reply(413,{'error':'invalid_length'})
                data=json.loads(self.rfile.read(size))
                if not isinstance(data,dict):raise ValueError('invalid_json')
                return self.reply(200,self.server.bot_gateway.cancel(data.get('bot'),data.get('request_id')))
            except (ValueError,TypeError,OSError):return self.reply(400,{'error':'cancel_failed'})
        if self.server.bot_gateway and self.path == '/bot/jobs':
            if self.headers.get('Content-Type', '').split(';')[0] != 'application/json':
                return self.reply(415, {'error': 'json_required'})
            try:
                size = int(self.headers.get('Content-Length', '-1'))
                if not 0 <= size <= 20000:
                    return self.reply(413, {'error': 'invalid_length'})
                data = json.loads(self.rfile.read(size))
                if not isinstance(data, dict):
                    raise ValueError('invalid_json')
                return self.reply(202, self.server.bot_gateway.start(data))
            except (ValueError, KeyError, OSError) as error:
                code = str(error)
                return self.reply(400, {'error': code if code in ('choose_configured_bot', 'invalid_message', 'invalid_request_id', 'request_id_conflict', 'bot_busy') else 'invalid_request'})
        if self.path != '/session':
            return self.reply(404, {'error': 'not_found'})
        if not self.server.xai_enabled:
            return self.reply(410, {'error': 'xai_disabled'})
        if self.headers.get('Content-Type', '').split(';')[0] != 'application/json':
            return self.reply(415, {'error': 'json_required'})
        try:
            size = int(self.headers.get('Content-Length', '-1'))
            if not 0 <= size <= 2048:
                return self.reply(413, {'error': 'invalid_length'})
            data = json.loads(self.rfile.read(size))
            if data != {}:
                return self.reply(400, {'error': 'empty_object_required'})
        except (ValueError, json.JSONDecodeError):
            return self.reply(400, {'error': 'invalid_json'})
        if not self.server.allow_session():
            return self.reply(429, {'error': 'local_rate_limit'})
        key = read_key()
        if not key:
            return self.reply(503, {'error': 'key_missing'})
        try:
            self.reply(200, mint_token(key))
        except urllib.error.HTTPError as error:
            # xAI also reports invalid credentials as HTTP 400. Classify the
            # response locally; never return raw provider messages or key text.
            rejected_key = False
            if error.code == 400:
                try:
                    detail = json.loads(error.read(65536)).get('error', '')
                    rejected_key = isinstance(detail, str) and any(
                        phrase in detail.lower() for phrase in ('incorrect api key', 'invalid api key'))
                except (ValueError, AttributeError):
                    pass
            code = {401: 'xai_auth', 403: 'xai_auth', 402: 'xai_billing',
                    429: 'xai_rate_limit'}.get(error.code, 'xai_unavailable')
            if rejected_key:
                code = 'xai_auth'
            self.reply(502, {'error': code})
        except (urllib.error.URLError, TimeoutError, socket.timeout, ValueError):
            self.reply(502, {'error': 'xai_unavailable'})


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=8787)
    parser.add_argument('--bind', default='127.0.0.1')
    parser.add_argument('--lan-cidr', help='Explicitly enable HTTP access from this private home subnet')
    parser.add_argument('--enable-bots', action='store_true', help='Enable the configured Cursor BDK bridge')
    parser.add_argument('--disable-xai', action='store_true', help='Use Cursor bots and tablet speech only; never read or use the xAI key')
    parser.add_argument('--tailscale-host', help='Exact private HTTPS hostname forwarded by Tailscale Serve to a loopback listener')
    parser.add_argument('--only-client', help='Accept non-loopback requests only from this single IP address')
    args = parser.parse_args()
    print(f'Grok voice broker listening on http://{args.bind}:{args.port}', flush=True)
    print('xAI disabled; using Cursor bots and tablet speech.' if args.disable_xai else 'Optional xAI voice credentials: backend/.env.', flush=True)
    TokenServer((args.bind, args.port), lan_cidr=args.lan_cidr, enable_bots=args.enable_bots, disable_xai=args.disable_xai, tailscale_host=args.tailscale_host, only_client=args.only_client).serve_forever()
