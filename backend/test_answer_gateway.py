import tempfile
import threading
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch
from activity_gateway import ActivityGateway
from answer_gateway import NOT_ANSWERABLE, AnswerGateway, SendUncertain, send_bot_message


def question_snapshot(name):
    return {'bot':name,'working':False,'entries':[],'needs_you':[{
        'id':'q1','kind':'question','question':'Merge the Tesla page fix?','context':'website',
        'options':[{'id':'yes','label':'Yes, merge it','recommended':True,'confirm':False},{'id':'no','label':'Not now'}],
        'created':10,'expires':None,'answerable_here':True}]}


def approval_snapshot(name):
    return {'bot':name,'working':True,'entries':[{'kind':'message','role':name,'text':'Merge it?','time':1}],'needs_you':[{
        'id':'handoff:req-1','kind':'approval','question':'Merge the Tesla page fix?','context':'',
        'options':[],'created':10,'expires':None,'answerable_here':False}]}


class AnswerTests(unittest.TestCase):
    def test_approval_is_not_sent_and_duplicates_stay_idle(self):
        calls=[]
        activity=ActivityGateway(lambda:[{'name':'Nova'}],approval_snapshot)
        gateway=AnswerGateway(activity,lambda *args:calls.append(args))
        key=str(uuid.uuid4())
        first=gateway.submit('Nova','handoff:req-1','approve',key)
        second=gateway.submit('Nova','handoff:req-1','approve',key)
        self.assertEqual(first,{'delivery':'failed:not_answerable_here','message':NOT_ANSWERABLE})
        self.assertEqual(second,first)
        self.assertEqual(calls,[])
        self.assertEqual(NOT_ANSWERABLE,'Approve this in the Grok Bot app.')

    def test_question_send_is_exactly_once_for_duplicates_and_concurrent_calls(self):
        entered,release=threading.Event(),threading.Event();calls=[]
        def send(name,text):
            calls.append((name,text));entered.set();release.wait(2)
        activity=ActivityGateway(lambda:[{'name':'Nova'}],question_snapshot)
        gateway=AnswerGateway(activity,send)
        key=str(uuid.uuid4())
        results=[]
        threads=[threading.Thread(target=lambda:results.append(gateway.submit('Nova','q1','yes',key))) for _ in range(2)]
        for thread in threads:thread.start()
        self.assertTrue(entered.wait(1))
        self.assertEqual(len(calls),1)
        release.set()
        for thread in threads:thread.join(2)
        self.assertFalse(any(thread.is_alive() for thread in threads))
        self.assertEqual(calls,[('Nova','Yes, merge it\nRe: website')])
        self.assertEqual(results,[{'delivery':'delivered'},{'delivery':'delivered'}])
        self.assertEqual(gateway.submit('Nova','q1','yes',key),{'delivery':'delivered'})
        self.assertEqual(len(calls),1)
        self.assertEqual(gateway.submit('Nova','q1','no',key)['delivery'],'failed:idempotency_conflict')
        self.assertEqual(len(calls),1)

    def test_failed_send_can_be_retried_and_a_missing_item_does_not_send(self):
        calls=[]
        def send(name,text):
            calls.append(text)
            if len(calls)==1:raise RuntimeError('private socket error')
        activity=ActivityGateway(lambda:[{'name':'Nova'}],question_snapshot)
        gateway=AnswerGateway(activity,send)
        key=str(uuid.uuid4())
        self.assertEqual(gateway.submit('Nova','q1','yes',key)['delivery'],'failed:send_failed')
        self.assertEqual(gateway.submit('Nova','q1','yes',key)['delivery'],'delivered')
        self.assertEqual(len(calls),2)
        self.assertNotIn('private',gateway.submit('Nova','missing','yes',str(uuid.uuid4()))['delivery'])
        self.assertEqual(gateway.submit('Nova','missing','yes',str(uuid.uuid4()))['delivery'],'failed:not_found')
        self.assertEqual(len(calls),2)

    def test_cursor_request_failure_is_retryable_and_unknown_bot_stays_a_client_error(self):
        calls=[]
        def send(name,text):
            calls.append(text)
            raise ValueError('cursor_request_failed')
        activity=ActivityGateway(lambda:[{'name':'Nova'}],question_snapshot)
        gateway=AnswerGateway(activity,send)
        key=str(uuid.uuid4())
        self.assertEqual(gateway.submit('Nova','q1','yes',key),{'delivery':'failed:send_failed'})
        self.assertEqual(gateway.submit('Nova','q1','yes',key),{'delivery':'failed:send_failed'})
        self.assertEqual(len(calls),2)
        def unknown(name,text):
            raise ValueError('choose_configured_bot')
        gateway.sender=unknown
        with self.assertRaises(ValueError) as raised:
            gateway.submit('Nova','q1','yes',str(uuid.uuid4()))
        self.assertEqual(str(raised.exception),'choose_configured_bot')

    def test_timeout_is_not_resent_and_the_key_survives_restart(self):
        calls=[]
        def send(name,text):
            calls.append((name,text))
            raise SendUncertain()
        activity=ActivityGateway(lambda:[{'name':'Nova'}],question_snapshot)
        with tempfile.TemporaryDirectory() as folder:
            store=Path(folder)/'answer-idempotency.json'
            gateway=AnswerGateway(activity,send,store_path=store)
            key=str(uuid.uuid4())
            self.assertEqual(gateway.submit('Nova','q1','yes',key),{'delivery':'delivered'})
            self.assertEqual(gateway.submit('Nova','q1','yes',key),{'delivery':'delivered'})
            self.assertEqual(calls,[('Nova','Yes, merge it\nRe: website')])
            restarted=AnswerGateway(activity,send,store_path=store)
            self.assertEqual(restarted.submit('Nova','q1','yes',key),{'delivery':'delivered'})
            self.assertEqual(len(calls),1)
            self.assertIn(key,store.read_text(encoding='utf-8'))

    def test_a_refused_send_is_not_remembered_as_delivered(self):
        calls=[]
        def send(name,text):
            calls.append(text)
            raise ValueError('cursor_request_failed')
        activity=ActivityGateway(lambda:[{'name':'Nova'}],question_snapshot)
        with tempfile.TemporaryDirectory() as folder:
            store=Path(folder)/'answer-idempotency.json'
            gateway=AnswerGateway(activity,send,store_path=store)
            key=str(uuid.uuid4())
            self.assertEqual(gateway.submit('Nova','q1','yes',key),{'delivery':'failed:send_failed'})
            restarted=AnswerGateway(activity,send,store_path=store)
            self.assertEqual(restarted.submit('Nova','q1','yes',key),{'delivery':'failed:send_failed'})
            self.assertEqual(len(calls),2)

    def test_silent_reply_does_not_wait_for_speech_and_a_timeout_is_uncertain(self):
        with patch('answer_gateway.call_tool', side_effect=TimeoutError('timed out')) as tool:
            with self.assertRaises(SendUncertain):
                send_bot_message('Nova','Yes, merge it')
        tool.assert_called_once_with('ask','Nova','Yes, merge it',0)
