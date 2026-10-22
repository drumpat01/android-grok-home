"""Exactly-once answers. Approvals are never resolved from the tablet."""
import json
import os
import threading
import uuid
from pathlib import Path

from bot_gateway import call_tool

NOT_ANSWERABLE = 'Approve this in the Grok Bot app.'
ANSWER_STORE = Path(__file__).with_name('bot-bridge') / 'data' / 'answer-idempotency.json'


class SendUncertain(Exception):
    """The ask timed out. The message may already have reached the bot."""


def send_bot_message(name, text):
    """Typed answer in silent reply mode.

    wait_seconds 0 delivers the message and does not collect a reply for speech.
    The bot's reply stays in the native conversation, which the tablet does not speak.
    """
    try:
        call_tool('ask', name, text, 0)
    except TimeoutError:
        raise SendUncertain from None


class AnswerGateway:
    def __init__(self, activity, sender=send_bot_message, store_path=None):
        self.activity, self.sender, self.store_path = activity, sender, store_path
        self.lock = threading.Lock()
        self.slots = {}
        self.order = []
        self.saved = {}
        self._load()

    def submit(self, bot, needs_you_id, option_id, idempotency_key):
        fingerprint = (bot, needs_you_id, option_id)
        with self.lock:
            slot = self.slots.get(idempotency_key)
            if slot is None:
                slot = {'fingerprint': fingerprint, 'result': None, 'event': threading.Event()}
                self.slots[idempotency_key] = slot
                self.order.append(idempotency_key)
                self._trim()
                owner = True
            elif slot['fingerprint'] != fingerprint:
                return {'delivery': 'failed:idempotency_conflict'}
            else:
                owner = False
        if not owner:
            slot['event'].wait(60)
            return slot['result'] or {'delivery': 'failed:send_failed'}
        try:
            result = self._perform(bot, needs_you_id, option_id, idempotency_key)
        except ValueError as error:
            self._forget(idempotency_key)
            if str(error) != 'choose_configured_bot':
                result = {'delivery': 'failed:send_failed'}
            else:
                with self.lock:
                    self.slots.pop(idempotency_key, None)
                    slot['event'].set()
                raise
        except Exception:
            self._forget(idempotency_key)
            result = {'delivery': 'failed:send_failed'}
        with self.lock:
            if result['delivery'] == 'failed:send_failed':
                self.slots.pop(idempotency_key, None)
            else:
                slot['result'] = result
            slot['event'].set()
        return result

    def _trim(self):
        removed = False
        while len(self.order) > 200:
            oldest = self.order.pop(0)
            slot = self.slots.get(oldest)
            if slot and slot['result'] is not None:
                self.slots.pop(oldest, None)
                if self.saved.pop(oldest, None) is not None:
                    removed = True
            elif oldest in self.slots and self.slots[oldest]['result'] is None:
                self.order.append(oldest)
                break
        if removed and self.store_path is not None:
            self._flush_locked()

    def _perform(self, bot, needs_you_id, option_id, idempotency_key):
        fingerprint = (bot, needs_you_id, option_id)
        snapshot = self.activity.get(bot)
        item = next((row for row in snapshot.get('needs_you') or [] if row.get('id') == needs_you_id), None)
        if item is None:
            return self._done(idempotency_key, fingerprint, {'delivery': 'failed:not_found'})
        if item.get('kind') == 'approval' or item.get('answerable_here') is not True:
            return self._done(idempotency_key, fingerprint, {'delivery': 'failed:not_answerable_here', 'message': NOT_ANSWERABLE})
        option = next((row for row in item.get('options') or [] if row.get('id') == option_id), None)
        if option is None:
            return self._done(idempotency_key, fingerprint, {'delivery': 'failed:unknown_option'})
        text = option['label']
        if item.get('context'):
            text = text + '\nRe: ' + item['context']
        delivered = {'delivery': 'delivered'}
        # Record the key before the ask. A timeout or crash after the message
        # leaves the process must not send it again.
        self._persist(idempotency_key, fingerprint, delivered)
        try:
            self.sender(bot, text)
        except SendUncertain:
            return delivered
        return delivered

    def _done(self, key, fingerprint, result):
        self._persist(key, fingerprint, result)
        return result

    def _persist(self, key, fingerprint, result):
        if self.store_path is None:
            return
        with self.lock:
            self.saved[key] = {'bot': fingerprint[0], 'needs_you_id': fingerprint[1], 'option_id': fingerprint[2], 'delivery': result.get('delivery'), 'message': result.get('message')}
            self._flush_locked()

    def _forget(self, key):
        if self.store_path is None:
            return
        with self.lock:
            if key not in self.saved:
                return
            self.saved.pop(key, None)
            self._flush_locked()

    def _load(self):
        if self.store_path is None or not self.store_path.exists():
            return
        try:
            data = json.loads(self.store_path.read_text(encoding='utf-8'))
        except (OSError, json.JSONDecodeError, UnicodeError):
            return
        rows = data.get('keys') if isinstance(data, dict) else None
        if not isinstance(rows, list):
            return
        for row in rows[-200:]:
            if not isinstance(row, dict):
                continue
            key, bot, needs_you_id, option_id, delivery = row.get('key'), row.get('bot'), row.get('needs_you_id'), row.get('option_id'), row.get('delivery')
            if not all(isinstance(value, str) and value for value in (key, bot, needs_you_id, delivery)):
                continue
            if not isinstance(option_id, str):
                continue
            result = {'delivery': delivery}
            if isinstance(row.get('message'), str):
                result['message'] = row['message']
            event = threading.Event()
            event.set()
            self.slots[key] = {'fingerprint': (bot, needs_you_id, option_id), 'result': result, 'event': event}
            self.order.append(key)
            self.saved[key] = {'bot': bot, 'needs_you_id': needs_you_id, 'option_id': option_id, 'delivery': delivery, 'message': result.get('message')}

    def _flush_locked(self):
        self.store_path.parent.mkdir(parents=True, exist_ok=True)
        rows = [{'key': key, **self.saved[key]} for key in self.order if key in self.saved]
        temporary = self.store_path.with_suffix('.json.tmp')
        temporary.write_text(json.dumps({'keys': rows[-200:]}, indent=2), encoding='utf-8')
        os.replace(temporary, self.store_path)


def parse_answer(data):
    if not isinstance(data, dict):
        raise ValueError('invalid_request')
    bot, needs_you_id, option_id, key = data.get('bot'), data.get('needs_you_id'), data.get('option_id'), data.get('idempotency_key')
    if not isinstance(bot, str) or not isinstance(needs_you_id, str) or not needs_you_id.strip():
        raise ValueError('invalid_request')
    if not isinstance(option_id, str):
        raise ValueError('invalid_request')
    try:
        key = str(uuid.UUID(key))
    except (ValueError, TypeError, AttributeError):
        raise ValueError('invalid_idempotency_key') from None
    return bot, needs_you_id.strip(), option_id, key
