import unittest
import threading
from activity_gateway import ActivityGateway

class ActivityTests(unittest.TestCase):
    def test_cache_and_error_backoff_keep_old_rows_but_never_stale_working(self):
        now=[0];calls=[]
        def read(name):
            calls.append(name)
            if len(calls)>1:raise RuntimeError('private provider body')
            return {'bot':name,'entries':[{'id':'1','kind':'contact'}],'working':True}
        cache=ActivityGateway(lambda:[{'name':'Nova'}],read,lambda:now[0])
        self.assertTrue(cache.get('Nova')['working']);cache.get('Nova');self.assertEqual(len(calls),1)
        now[0]=5;stale=cache.get('Nova');self.assertTrue(stale['stale']);self.assertIsNone(stale['working']);self.assertEqual(len(stale['entries']),1)
        now[0]=20;cache.get('Nova');self.assertEqual(len(calls),2)
        now[0]=26;cache.get('Nova');self.assertEqual(len(calls),3)
        self.assertNotIn('private',str(stale))
    def test_unknown_bot_does_not_call_reader(self):
        cache=ActivityGateway(lambda:[{'name':'Nova'}],lambda _:self.fail('called reader'))
        with self.assertRaises(ValueError):cache.get('Typo')
    def test_parallel_requests_share_one_refresh(self):
        calls=[];cache=ActivityGateway(lambda:[{'name':'Nova'}],lambda name:(calls.append(name) or {'entries':[],'working':False}))
        threads=[threading.Thread(target=lambda:cache.get('Nova')) for _ in range(8)]
        for thread in threads:thread.start()
        for thread in threads:thread.join()
        self.assertEqual(len(calls),1)

    def test_summary_omits_bodies_contacts_and_user_messages(self):
        from activity_gateway import activity_summary
        snapshot={'bot':'Nova','stale':False,'working':True,'entries':[
            {'kind':'message','role':'Nova','id':'reply','updatedSeq':'80','seq':'70','time':123,'text':'private reply'},
            {'kind':'contact','id':'contact','text':'private contact'},
            {'kind':'message','role':'You','id':'user','text':'private request'}]}
        summary=activity_summary(snapshot)
        self.assertEqual(summary,{'bot':'Nova','stale':False,'working':True,'reply_id':'reply','reply_revision':'80','reply_seq':'70','reply_time':123})
        self.assertNotIn('private',str(summary));self.assertNotIn('entries',summary)


class RoutingDisplayTests(unittest.TestCase):
    def test_coordination_protocol_has_readable_labels_without_mutating_source(self):
        from activity_gateway import display_snapshot
        prompt='Choose the best existing bot for the user request below, based on your knowledge of the team. Rules.\nUSER REQUEST:\nReview my website'
        original={'bot':'Max','entries':[{'kind':'message','role':'You','text':prompt},{'kind':'message','role':'Max','text':'ROUTE: Nova'}]}
        shown=display_snapshot(original)
        self.assertEqual(shown['entries'][0]['text'],'Who can help? Review my website')
        self.assertEqual(shown['entries'][1]['text'],'Recommended bot: Nova')
        self.assertEqual(original['entries'][0]['text'],prompt)
