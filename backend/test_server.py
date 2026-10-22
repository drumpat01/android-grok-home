import json
import io
import ipaddress
from pathlib import Path
import tempfile
import threading
import unittest
import uuid
from unittest.mock import patch
import urllib.error
import urllib.request
from activity_gateway import ActivityGateway
from answer_gateway import AnswerGateway
import server


class BrokerTests(unittest.TestCase):
    def setUp(self):
        self.server = server.TokenServer(('127.0.0.1', 0))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f'http://127.0.0.1:{self.server.server_port}'
        self.key = patch('server.read_key', return_value='TEST_ONLY_NOT_A_REAL_KEY').start()
        self.addCleanup(patch.stopall)
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)

    def request(self, path='/session', body=b'{}', headers=None, method='POST'):
        req = urllib.request.Request(self.url + path, data=body if method=='POST' else None,
            headers=headers or {'Content-Type': 'application/json'}, method=method)
        try:
            response = urllib.request.urlopen(req, timeout=3)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, json.loads(response.read()), response.headers

    def test_missing_key_never_calls_xai(self):
        self.key.return_value = ''
        with patch('server.mint_token') as mint:
            status, data, _ = self.request()
            self.assertEqual((status, data['error']), (503, 'key_missing'))
            mint.assert_not_called()

    def test_only_ephemeral_credentials_returned(self):
        with patch('server.mint_token', return_value={'value':'TEST_EPHEMERAL','expires_at':123}):
            status, data, headers = self.request()
        self.assertEqual(status, 200)
        self.assertEqual(data['value'], 'TEST_EPHEMERAL')
        self.assertNotIn('TEST_ONLY_NOT_A_REAL_KEY', json.dumps(data))
        self.assertEqual(headers['Cache-Control'], 'no-store')

    def test_browser_origin_and_rebinding_rejected(self):
        for headers in ({'Origin':'https://example.com'}, {'Host':'attacker.example'}, {'Sec-Fetch-Site':'cross-site'}):
            self.assertEqual(self.request(headers=headers)[0], 403)

    def test_native_activity_route_keeps_existing_client_boundaries(self):
        from activity_gateway import ActivityGateway
        self.server.activity_gateway=ActivityGateway(lambda:[{'name':'Nova'}],lambda name:{'bot':name,'entries':[{'id':'1','kind':'contact','from':'Max','to':'Nova'}],'working':False})
        status,data,_=self.request('/bot/activity?bot=Nova',method='GET')
        self.assertEqual(status,200);self.assertEqual(data['entries'][0]['from'],'Max')
        self.assertEqual(self.request('/bot/activity?bot=Typo',method='GET')[0],400)
        self.assertEqual(self.request('/bot/activity?bot=Nova&bot=Max',method='GET')[0],400)
        self.assertEqual(self.request('/bot/activity?bot=Nova',headers={'Origin':'https://example.com'},method='GET')[0],403)

    def test_compact_activity_summary_uses_same_roster_and_browser_boundaries(self):
        from activity_gateway import ActivityGateway
        body='4.5.2 in review '+('x'*80)+' PRIVATE-TAIL'
        self.server.activity_gateway=ActivityGateway(lambda:[{'name':'Nova'}],lambda name:{'bot':name,'entries':[{'id':'r','kind':'message','role':'Nova','text':body,'time':3}],'working':False})
        status,data,_=self.request('/bot/activity?bot=Nova&summary=1',method='GET')
        self.assertEqual(status,200);self.assertEqual(data['reply_id'],'r');self.assertNotIn('entries',data);self.assertNotIn(body,str(data));self.assertNotIn('PRIVATE-TAIL',str(data))
        self.assertLessEqual(len(data['latestUnreadPreview']),60);self.assertTrue(data['latestUnreadPreview'].startswith('4.5.2 in review'))
        self.assertEqual(self.request('/bot/activity?bot=Typo&summary=1',method='GET')[0],400)
        self.assertEqual(self.request('/bot/activity?bot=Nova&summary=1',headers={'Origin':'https://example.com'},method='GET')[0],403)

    def test_update_metadata_and_binary_are_pinned_and_private(self):
        from app_updates import UpdateStore,publish
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);apk=root/'app.apk';apk.write_bytes(b'UPDATE_TEST_APK');publish(apk,23,'test',root/'releases');self.server.update_store=UpdateStore(root/'releases')
            status,metadata,_=self.request('/app/update',method='GET');self.assertEqual(status,200)
            with urllib.request.urlopen(self.url+metadata['apk_path']) as response:self.assertEqual(response.read(),b'UPDATE_TEST_APK')
            self.assertEqual(self.request('/app/update.apk?sha256=old',method='GET')[0],409)
            self.assertEqual(self.request('/app/update',headers={'Host':'other.example'},method='GET')[0],403)
            self.assertEqual(self.request(metadata['apk_path'],headers={'Origin':'https://example.com'},method='GET')[0],403)

    def test_tailscale_proxy_accepts_only_exact_host_and_no_browser_origin(self):
        self.server.tailscale_host = 'tablet-test.example.ts.net'
        headers = {'Host': 'tablet-test.example.ts.net:9443'}
        self.assertEqual(self.request('/health', headers=headers, method='GET')[0], 200)
        self.assertEqual(self.request('/health', headers={'Host': 'other.example.ts.net'}, method='GET')[0], 403)
        self.assertEqual(self.request('/health', headers={**headers, 'Origin': 'https://tablet-test.example.ts.net:9443'}, method='GET')[0], 403)

    def test_tailscale_proxy_configuration_is_loopback_only(self):
        for hostname in ('example.com', 'test.ts.net/evil', '*.ts.net'):
            with self.assertRaises(ValueError):
                server.TokenServer(('127.0.0.1', 0), tailscale_host=hostname)
        with self.assertRaises(ValueError):
            server.TokenServer(('192.168.1.2', 0), lan_cidr='192.168.1.0/24', tailscale_host='test.ts.net')

    def test_request_validation(self):
        self.assertEqual(self.request(headers={'Content-Type':'text/plain'})[0],415)
        self.assertEqual(self.request(body=b'bad json')[0],400)
        self.assertEqual(self.request(body=b'{"session":{}}')[0],400)
        self.assertEqual(self.request(body=b'x'*2049)[0],413)
        self.assertEqual(self.request(path='/not-found')[0],404)

    def test_rate_limit(self):
        self.key.return_value = ''
        for _ in range(6): self.assertEqual(self.request()[0],503)
        self.assertEqual(self.request()[0],429)

    def test_upstream_auth_details_are_not_exposed(self):
        error = urllib.error.HTTPError(server.XAI_ENDPOINT,401,'TEST_ONLY_NOT_A_REAL_KEY',None,None)
        with patch('server.mint_token', side_effect=error):
            status, data, _ = self.request()
        self.assertEqual((status,data), (502,{'error':'xai_auth'}))

    def test_health_never_returns_key(self):
        status, data, _ = self.request('/health',method='GET')
        self.assertEqual(status, 200)
        started = data.pop('started_at')
        self.assertIsInstance(started, int)
        self.assertGreater(started, 1_700_000_000)
        self.assertEqual(data, {'service':'grok-home-voice','key_configured':True,'xai_enabled':True,'bots_enabled':False,'activity_enabled':False,'updates_enabled':True,'team_stage_enabled':False,'raise_hand_enabled':False,'version':server.service_version()})
        self.assertNotIn('TEST_ONLY_NOT_A_REAL_KEY', json.dumps(data))
        again = self.request('/health', method='GET')[1]['started_at']
        self.assertEqual(started, again)

    def test_raise_hand_answer_route_does_not_approve_and_is_idempotent(self):
        calls=[]
        def read(name):
            return {'bot':name,'working':True,'entries':[{'kind':'message','role':name,'id':'r','text':'Merge it?','time':4}],
                    'needs_you':[{'id':'handoff:req-1','kind':'approval','question':'Merge the Tesla page fix?','context':'','options':[],'created':1,'expires':None,'answerable_here':False}]}
        self.server.activity_gateway=ActivityGateway(lambda:[{'name':'Nova'}],read)
        self.server.answer_gateway=AnswerGateway(self.server.activity_gateway,lambda *args:calls.append(args))
        body=json.dumps({'bot':'Nova','needs_you_id':'handoff:req-1','option_id':'approve','idempotency_key':str(uuid.uuid4())}).encode()
        status,data,_=self.request('/bot/answer',body=body)
        again=self.request('/bot/answer',body=body)
        self.assertEqual(status,200)
        self.assertEqual(data,{'delivery':'failed:not_answerable_here','message':'Approve this in the Grok Bot app.'})
        self.assertEqual(again[1],data)
        self.assertEqual(calls,[])
        self.assertEqual(self.request('/bot/answer',body=b'{"bot":"Nova"}')[0],400)
        self.assertEqual(self.request('/bot/answer',body=body,headers={'Origin':'https://example.com','Content-Type':'application/json'})[0],403)
        health=self.request('/health',method='GET')[1]
        self.assertTrue(health['raise_hand_enabled'])
        summary=self.request('/bot/activity?bot=Nova&summary=1',method='GET')[1]
        self.assertEqual(summary['needs_you'][0]['question_short'],'Merge the Tesla page fix?')
        self.assertFalse(summary['needs_you'][0]['question_short_truncated'])
        self.assertEqual(summary['latestUnreadPreview'],'Merge it?')
        self.assertNotIn('Merge it?',summary['needs_you'][0]['question'])

    def test_disabled_xai_never_reads_key_or_mints_token(self):
        self.server.xai_enabled = False
        self.key.reset_mock()
        with patch('server.mint_token') as mint:
            status, data, _ = self.request('/health', method='GET')
            self.assertEqual(status, 200)
            self.assertFalse(data['key_configured'])
            self.assertFalse(data['xai_enabled'])
            status, data, _ = self.request()
            self.assertEqual((status, data), (410, {'error': 'xai_disabled'}))
            self.key.assert_not_called()
            mint.assert_not_called()

    def test_upstream_http_400_invalid_key_is_auth_error(self):
        body=io.BytesIO(b'{"code":"invalid argument","error":"Incorrect API key provided."}')
        error=urllib.error.HTTPError(server.XAI_ENDPOINT,400,'Bad Request',None,body)
        with patch('server.mint_token',side_effect=error):
            status,data,_=self.request()
        self.assertEqual((status,data),(502,{'error':'xai_auth'}))

    def test_public_binding_disallowed(self):
        with self.assertRaises(ValueError): server.TokenServer(('0.0.0.0',0))

    def test_lan_host_and_client_are_limited_to_configured_subnet(self):
        self.server.lan=ipaddress.ip_network('192.168.1.0/24')
        with patch('server.mint_token',return_value={'value':'TEST_EPHEMERAL','expires_at':123}):
            self.assertEqual(self.request(headers={'Host':'192.168.1.20:8787','Content-Type':'application/json'})[0],200)
        handler=object.__new__(server.Handler)
        handler.server=self.server;handler.headers={'Host':'192.168.1.20:8787'}
        handler.client_address=('192.168.1.42',1234);self.assertTrue(handler.local_request())
        handler.client_address=('192.168.2.42',1234);self.assertFalse(handler.local_request())
        handler.client_address=('8.8.8.8',1234);self.assertFalse(handler.local_request())

    def test_lan_cannot_be_public_or_unspecified(self):
        for address,cidr in [('0.0.0.0','192.168.1.0/24'),('192.168.2.10','192.168.1.0/24'),('8.8.8.8','8.8.8.0/24')]:
            with self.assertRaises(ValueError):server.TokenServer((address,0),lan_cidr=cidr)

    def test_env_file_quotes_and_bom(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict('os.environ',{},clear=True):
            env=Path(folder)/'.env'
            env.write_text('\ufeffXAI_API_KEY="TEST_LOCAL"\n',encoding='utf-8')
            # read_key is mocked for HTTP tests; exercise the original via saved callable.
            self.assertEqual(ORIGINAL_READ_KEY(env),'TEST_LOCAL')


ORIGINAL_READ_KEY = server.read_key
if __name__ == '__main__': unittest.main()
