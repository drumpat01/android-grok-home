import threading
import time
import unittest
import uuid
from bot_gateway import BotGateway


class BotGatewayTests(unittest.TestCase):
    def test_completed_message_is_visible_before_turn_finishes(self):
        checking, finish = threading.Event(), threading.Event()
        calls = []
        def call(action, name, message):
            calls.append(action)
            if action == 'ask':
                return {'status': 'running', 'reply': 'First message.'}
            checking.set()
            finish.wait(2)
            return {'status': 'finished', 'reply': 'Second message.'}
        gateway = BotGateway(lambda: [{'name': 'Max'}], call)
        data = {'request_id': str(uuid.uuid4()), 'bot': 'Max', 'message': 'Hello'}
        gateway.start(data)
        try:
            self.assertTrue(checking.wait(1))
            partial = gateway.get(data['request_id'])
            self.assertEqual((partial['status'], partial['reply']), ('running', 'First message.'))
        finally:
            finish.set()
        final = self.wait(gateway, data['request_id'])
        self.assertEqual(final['reply'], 'First message.\n\nSecond message.')
        self.assertEqual(calls, ['ask', 'check'])

    def test_cancel_waits_for_accepted_send_then_interrupts_without_checking(self):
        sending,release=threading.Event(),threading.Event();calls=[]
        def call(action,name,message):
            calls.append(action)
            if action=='ask': sending.set();release.wait(2);return {'status':'running'}
            return {'interrupted':True}
        gateway=BotGateway(lambda:[{'name':'Max'}],call)
        data={'request_id':str(uuid.uuid4()),'bot':'Max','message':'Work'};gateway.start(data);self.assertTrue(sending.wait(1))
        results=[];stop=threading.Thread(target=lambda:results.append(gateway.cancel('Max',data['request_id'])));stop.start()
        time.sleep(.03);self.assertEqual(gateway.get(data['request_id'])['status'],'cancelling')
        with self.assertRaisesRegex(ValueError,'bot_busy'):gateway.start({**data,'request_id':str(uuid.uuid4())})
        release.set();stop.join(2);self.assertFalse(stop.is_alive());self.assertEqual(results[0]['status'],'cancelled');self.assertEqual(calls,['ask','interrupt'])
        time.sleep(.05);self.assertEqual(gateway.get(data['request_id'])['status'],'cancelled')

    def test_cancel_failure_is_not_reported_as_success(self):
        def call(action,*args):raise RuntimeError('private')
        gateway=BotGateway(lambda:[{'name':'Max'}],call)
        with self.assertRaisesRegex(ValueError,'cancel_failed'):gateway.cancel('Max')

    def test_routing_accepts_only_exact_existing_names(self):
        for reply,expected in [('ROUTE: Nova','finished'),('ROUTE: Unknown','error'),('Here is Nova','error')]:
            calls=[]
            def call(action,name,message):calls.append((action,name,message));return {'status':'finished','reply':reply}
            gateway=BotGateway(lambda:[{'name':'Max'},{'name':'Nova'}],call)
            data={'request_id':str(uuid.uuid4()),'bot':'Max','action':'route','message':'Help with website'};gateway.start(data);result=self.wait(gateway,data['request_id'])
            self.assertEqual(result['status'],expected);self.assertEqual(calls[0][0],'ask');self.assertIn('Do not perform the task',calls[0][2]);self.assertEqual(len(calls),1)
            if expected=='finished':self.assertEqual(result['route_bot'],'Nova')

    def wait(self, gateway, request_id):
        for _ in range(100):
            job = gateway.get(request_id)
            if job['status'] != 'running':
                return job
            time.sleep(.01)
        self.fail('job did not finish')

    def test_delivers_current_reply_only_and_idempotent_send(self):
        calls = []
        def call(action, name, message):
            calls.append((action, name, message))
            return {'status': 'finished', 'reply': 'Hello there', 'earlierReplies': ['Private older reply']}
        gateway = BotGateway(lambda: [{'name': 'Max'}], call)
        data = {'request_id': str(uuid.uuid4()), 'bot': 'Max', 'message': 'Hello'}
        gateway.start(data)
        result = self.wait(gateway, data['request_id'])
        gateway.start(data)
        self.assertEqual(calls, [('ask', 'Max', 'Hello')])
        self.assertEqual(result['reply'], 'Hello there')
        self.assertNotIn('earlierReplies', result)
        self.assertNotIn('_message', result)

    def test_refuses_unknown_bot_and_reused_id_with_changed_message(self):
        gateway = BotGateway(lambda: [{'name': 'Max'}], lambda *args: {'status': 'finished'})
        data = {'request_id': str(uuid.uuid4()), 'bot': 'Other', 'message': 'Hello'}
        with self.assertRaisesRegex(ValueError, 'choose_configured_bot'): gateway.start(data)
        data['bot'] = 'Max'
        gateway.start(data)
        self.wait(gateway, data['request_id'])
        data['message'] = 'Changed'
        with self.assertRaisesRegex(ValueError, 'request_id_conflict'): gateway.start(data)

    def test_busy_and_failure_do_not_resend_or_expose_provider_details(self):
        gate = threading.Event()
        calls = []
        def call(*args):
            calls.append(args)
            gate.wait(1)
            raise RuntimeError('PRIVATE_PROVIDER_DETAIL')
        gateway = BotGateway(lambda: [{'name': 'Max'}], call)
        data = {'request_id': str(uuid.uuid4()), 'bot': 'Max', 'message': 'Hello'}
        gateway.start(data)
        with self.assertRaisesRegex(ValueError, 'bot_busy'):
            gateway.start({**data, 'request_id': str(uuid.uuid4())})
        gate.set()
        job = self.wait(gateway, data['request_id'])
        self.assertEqual(job['error'], 'check_reply')
        self.assertNotIn('PRIVATE_PROVIDER_DETAIL', str(job))
        self.assertEqual(len(calls), 1)

    def test_followup_checks_never_resend_accepted_message(self):
        calls = []
        def call(action, name, message):
            calls.append(action)
            return {'status': 'running' if action == 'ask' else 'finished', 'reply': 'One' if action == 'ask' else 'Two'}
        gateway = BotGateway(lambda: [{'name': 'Max'}], call)
        data = {'request_id': str(uuid.uuid4()), 'bot': 'Max', 'message': 'Hello'}
        gateway.start(data)
        for _ in range(200):
            job = gateway.get(data['request_id'])
            if job['status'] == 'finished': break
            time.sleep(.01)
        self.assertEqual(job['reply'], 'One\n\nTwo')
        self.assertEqual(calls, ['ask', 'check'])


if __name__ == '__main__': unittest.main()
