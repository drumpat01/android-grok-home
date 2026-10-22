import unittest
import threading
from activity_gateway import ActivityGateway, activity_summary, attention_priority, question_short

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

    def test_summary_omits_full_bodies_contacts_and_user_messages(self):
        from activity_gateway import activity_summary
        body='private reply '+('word '*20)+'SECRET-BODY'
        snapshot={'bot':'Nova','stale':False,'working':True,'entries':[
            {'kind':'message','role':'Nova','id':'reply','updatedSeq':'80','seq':'70','time':123,'text':body},
            {'kind':'contact','id':'contact','text':'private contact'},
            {'kind':'message','role':'You','id':'user','text':'private request SECRET-USER'}]}
        summary=activity_summary(snapshot)
        self.assertEqual(summary['bot'],'Nova');self.assertFalse(summary['stale']);self.assertTrue(summary['working'])
        self.assertEqual(summary['reply_id'],'reply');self.assertEqual(summary['reply_revision'],'80')
        self.assertEqual(summary['reply_seq'],'70');self.assertEqual(summary['reply_time'],123)
        self.assertEqual(summary['needs_you'],[]);self.assertIsNone(summary['last_run'])
        self.assertNotIn('entries',summary);self.assertNotIn(body,str(summary))
        self.assertNotIn('private contact',str(summary));self.assertNotIn('SECRET-USER',str(summary))
        self.assertLessEqual(len(summary['latestUnreadPreview']),60)
        self.assertTrue(summary['latestUnreadPreview'].startswith('private reply'))
        self.assertNotIn('SECRET-BODY',summary['latestUnreadPreview'])


class RoutingDisplayTests(unittest.TestCase):
    def test_coordination_protocol_has_readable_labels_without_mutating_source(self):
        from activity_gateway import display_snapshot
        prompt='Choose the best existing bot for the user request below, based on your knowledge of the team. Rules.\nUSER REQUEST:\nReview my website'
        original={'bot':'Max','entries':[{'kind':'message','role':'You','text':prompt},{'kind':'message','role':'Max','text':'ROUTE: Nova'}]}
        shown=display_snapshot(original)
        self.assertEqual(shown['entries'][0]['text'],'Who can help? Review my website')
        self.assertEqual(shown['entries'][1]['text'],'Recommended bot: Nova')
        self.assertEqual(original['entries'][0]['text'],prompt)


class PreviewTests(unittest.TestCase):
    def preview(self, text):
        from activity_gateway import unread_preview
        return unread_preview(text)

    def test_strips_markdown_links_and_code(self):
        self.assertEqual(self.preview('**4.5.2** in review'),'4.5.2 in review')
        self.assertEqual(self.preview('__4.5.2__ in review'),'4.5.2 in review')
        self.assertEqual(self.preview('~~4.5.2~~ in review'),'4.5.2 in review')
        self.assertEqual(self.preview('*4.5.2* in review'),'4.5.2 in review')
        self.assertEqual(self.preview('[4.5.2 in review](https://ci.example/sk-live-SECRET)'),'4.5.2 in review')
        self.assertEqual(self.preview('![status](https://cdn.example/secret.png)'),'status')
        self.assertEqual(self.preview('See <https://ci.example/sk-live-SECRET>'),'See')
        self.assertEqual(self.preview('`npm test` passed'),'npm test passed')
        self.assertEqual(self.preview('```js\nconst ready = true\n```\nShip it'),'const ready = true Ship it')
        self.assertEqual(self.preview('# 4.5.2 in review'),'4.5.2 in review')
        self.assertEqual(self.preview('> 4.5.2 in review'),'4.5.2 in review')
        self.assertEqual(self.preview('- 4.5.2 in review'),'4.5.2 in review')
        self.assertEqual(self.preview('1. 4.5.2 in review'),'4.5.2 in review')
        self.assertEqual(self.preview('<b>4.5.2</b> in review'),'4.5.2 in review')
        self.assertNotIn('SECRET',self.preview('[ok](https://example.com/sk-live-SECRET)'))
        self.assertEqual(self.preview('snake_case stays'),'snake_case stays')

    def test_collapses_newlines(self):
        self.assertEqual(self.preview('Hello\n\nworld'),'Hello world')
        self.assertEqual(self.preview('Hello\r\nworld'),'Hello world')
        self.assertEqual(self.preview('Hello\n\nworld\n- item'),'Hello world item')
        self.assertEqual(self.preview('  4.5.2   \n  in\treview  '),'4.5.2 in review')

    def test_truncates_on_a_word_boundary(self):
        text='Alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron'
        preview=self.preview(text)
        self.assertTrue(preview.endswith('\u2026'))
        self.assertLessEqual(len(preview),60)
        body=preview[:-1]
        self.assertTrue(text.startswith(body))
        self.assertTrue(text.startswith(body+' ') or text==body)
        self.assertFalse(body.endswith(' '))
        hard=('ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'*4)
        cut=self.preview(hard)
        self.assertTrue(cut.endswith('\u2026'))
        self.assertLessEqual(len(cut),60)
        self.assertTrue(hard.startswith(cut[:-1]))

    def test_empty_text_is_empty(self):
        self.assertEqual(self.preview(None),'')
        self.assertEqual(self.preview(''),'')
        self.assertEqual(self.preview('   \n\t  '),'')
        self.assertEqual(self.preview('```\n```'),'')
        self.assertEqual(self.preview('[ ](https://example.com/secret)'),'')
        self.assertEqual(self.preview('https://example.com/sk-live-SECRET'),'')

    def test_emoji_and_combining_marks_stay_intact(self):
        self.assertEqual(self.preview('👍 4.5.2 in review'),'👍 4.5.2 in review')
        self.assertEqual(self.preview('👨‍👩‍👧‍👦 says hello'),'👨‍👩‍👧‍👦 says hello')
        self.assertEqual(self.preview('🇺🇸 ready'),'🇺🇸 ready')
        self.assertEqual(self.preview('Cafe\u0301 is open'),'Cafe\u0301 is open')
        marks='e\u0301'*50
        cut=self.preview(marks)
        self.assertTrue(cut.endswith('\u2026'))
        body=cut[:-1]
        self.assertEqual(len(body)%2,0)
        self.assertFalse(body.endswith('e'))
        self.assertLessEqual(len(cut),60)
        faces=self.preview('😀'*80)
        self.assertTrue(faces.endswith('\u2026'))
        self.assertLessEqual(len(faces),60)
        self.assertNotIn('\ufffd',faces)
        sentence=self.preview('😀start '+('token '*30))
        self.assertTrue(sentence.startswith('😀start '))
        self.assertTrue(sentence.endswith('\u2026'))
        self.assertFalse(sentence.endswith('tok\u2026'))

    def test_summary_uses_the_newest_bot_reply_only(self):
        from activity_gateway import activity_summary
        secret='SECRET-USER-BODY'
        snapshot={'bot':'Nova','stale':False,'working':False,'entries':[
            {'kind':'message','role':'Nova','id':'old','seq':'1','time':1,'text':'older note'},
            {'kind':'contact','role':'Nova','text':'SECRET contact body'},
            {'kind':'message','role':'You','id':'user','seq':'2','time':2,'text':secret},
            {'kind':'message','role':'Nova','id':'new','seq':'3','time':3,'text':'**4.5.2** in review'}]}
        summary=activity_summary(snapshot)
        self.assertEqual(summary['reply_id'],'new')
        self.assertEqual(summary['latestUnreadPreview'],'4.5.2 in review')
        self.assertNotIn(secret,str(summary))
        self.assertNotIn('older note',str(summary))
        self.assertNotIn('SECRET contact',str(summary))
        self.assertEqual(summary['needs_you'],[])
        self.assertIsNone(summary['last_run'])


def handoff(question='Merge the Tesla page fix?', created=40, **extra):
    item={'id':'handoff:req-1','kind':'approval','question':question,'context':'website','title':extra.get('title',''),
          'options':[{'id':'approve','label':'Approve'}],'created':created,'expires':None,'answerable_here':True}
    return item

class RaiseYourHandTests(unittest.TestCase):
    def snapshot(self, **extra):
        base={'bot':'Nova','stale':False,'working':False,'entries':[{'kind':'message','role':'Nova','id':'reply','text':'Should I merge the Tesla page fix?','time':30}]}
        base.update(extra)
        return base

    def test_question_mark_reply_is_unread_only(self):
        summary=activity_summary(self.snapshot())
        self.assertEqual(summary['needs_you'],[])
        self.assertEqual(summary['reply_id'],'reply')
        self.assertIsNone(summary['last_run'])
        self.assertEqual(summary['latestUnreadPreview'],'Should I merge the Tesla page fix?')
        self.assertNotIn('Should I merge',str(summary['needs_you']))

    def test_native_approval_is_needs_you_and_keeps_working(self):
        shown=ActivityGateway(lambda:[{'name':'Nova'}],lambda name:self.snapshot(working=True,needs_you=[handoff()])).get('Nova')
        self.assertEqual(shown['needs_you'][0]['kind'],'approval')
        self.assertFalse(shown['needs_you'][0]['answerable_here'])
        self.assertEqual(shown['needs_you'][0]['options'],[])
        self.assertEqual(shown['needs_you'][0]['question'],'Merge the Tesla page fix?')
        self.assertEqual(shown['needs_you'][0]['question_short'],'Merge the Tesla page fix?')
        self.assertFalse(shown['needs_you'][0]['question_short_truncated'])
        self.assertTrue(shown['working'])
        self.assertEqual(attention_priority(needs_you=True,working=True),'needs_you')
        self.assertEqual(attention_priority(needs_you=True,working=False,done=True),'needs_you')
        self.assertEqual(attention_priority(needs_you=False,working=True,done=True),'done')
        self.assertEqual(attention_priority(needs_you=False,working=True,stale=True),'idle')
        self.assertEqual(attention_priority(needs_you=True,working=False,stale=True),'needs_you')

    def test_short_form_keeps_fitting_text_and_marks_a_cut(self):
        exact,exact_cut=question_short('Merge the Tesla page fix?')
        self.assertEqual((exact,exact_cut,len(exact)),('Merge the Tesla page fix?',False,25))
        self.assertEqual(question_short('the Tesla page?'),('the Tesla page?',False))
        self.assertEqual(question_short('Ship it!'),('Ship it!',False))
        self.assertEqual(question_short('', ''),('',False))
        full='Merge the Tesla page fix now please?'
        short,truncated=question_short(full)
        self.assertEqual(short,'Merge the Tesla page fix')
        self.assertEqual(len(short),24)
        self.assertTrue(truncated)
        self.assertTrue(full.startswith(short))
        self.assertTrue(question_short('the Tesla page needs a longer instruction today please')[0].startswith('the '))
        self.assertEqual(question_short('Ship it! '+'y'*30),('Ship it',True))
        self.assertEqual(question_short('Go? '+'x'*30),('Go?',True))
        self.assertEqual(question_short('Approve, immediately, something longer'),('Approve, immediately',True))
        self.assertEqual(question_short('A'*30),('A'*24,True))
        self.assertEqual(question_short('Merge the Tesla page fix?','A'*30),('A'*24,True))
        shown=ActivityGateway(lambda:[{'name':'Nova'}],lambda name:self.snapshot(needs_you=[handoff(full,title='Merge fix?')])).get('Nova')
        self.assertEqual(shown['needs_you'][0]['question'],full)
        self.assertEqual(shown['needs_you'][0]['question_short'],'Merge fix?')
        self.assertFalse(shown['needs_you'][0]['question_short_truncated'])
        empty=ActivityGateway(lambda:[{'name':'Nova'}],lambda name:self.snapshot(needs_you=[handoff('   ')])).get('Nova')
        self.assertEqual(empty['needs_you'][0]['question'],'')
        self.assertEqual(empty['needs_you'][0]['question_short'],'')
        self.assertFalse(empty['needs_you'][0]['question_short_truncated'])

    def test_overlong_first_word_keeps_its_first_24_characters(self):
        word='!'*40
        short,truncated=question_short(word)
        self.assertEqual(short,'!'*24)
        self.assertEqual(len(short),24)
        self.assertTrue(truncated)
        mixed='A'*22+'!!'+'B'*20
        self.assertEqual(question_short(mixed),(mixed[:24],True))
        self.assertNotEqual(short,'')

    def test_missing_timestamp_uses_poll_time_so_an_older_you_message_stays(self):
        item=handoff()
        item['created']=0
        wall=[100.0]
        gate=ActivityGateway(lambda:[{'name':'Nova'}],lambda name:self.snapshot(entries=[{'kind':'message','role':'You','text':'Earlier','time':10}],needs_you=[item]),wall=lambda:wall[0])
        shown=gate.get('Nova')
        self.assertEqual(shown['needs_you'][0]['id'],'handoff:req-1')
        self.assertEqual(shown['needs_you'][0]['created'],100000)

    def test_cached_hand_drops_thirty_minutes_after_its_last_poll(self):
        mono=[0]
        wall=[1_700_000_000.0]
        calls=[0]
        def read(name):
            calls[0]+=1
            if calls[0]==1:return self.snapshot(needs_you=[handoff()])
            raise RuntimeError('down')
        gate=ActivityGateway(lambda:[{'name':'Nova'}],read,clock=lambda:mono[0],wall=lambda:wall[0])
        self.assertEqual(gate.get('Nova')['needs_you'][0]['id'],'handoff:req-1')
        wall[0]+=30*60
        self.assertEqual(len(gate.get('Nova')['needs_you']),1)
        wall[0]+=1
        self.assertEqual(gate.get('Nova')['needs_you'],[])
        self.assertEqual(calls[0],1)
        mono[0]=5
        self.assertEqual(gate.get('Nova')['needs_you'],[])
        calls[0]=0
        wall[0]+=60
        mono[0]=30
        fresh=gate.get('Nova')
        self.assertEqual(fresh['needs_you'][0]['id'],'handoff:req-1')
        wall[0]+=29*60
        mono[0]=31
        self.assertEqual(len(gate.get('Nova')['needs_you']),1)

    def test_resolution_you_message_new_run_and_restart_clear_the_hand(self):
        now=[0]
        def read(name):
            if now[0]<1:return self.snapshot(working=True,needs_you=[handoff()])
            if now[0]<2:return self.snapshot(working=False,entries=[{'kind':'message','role':'You','text':'Done','time':50}],needs_you=[handoff()])
            return self.snapshot(working=False,last_run={'outcome':'cancelled','at':60})
        gate=ActivityGateway(lambda:[{'name':'Nova'}],read,lambda:now[0])
        self.assertEqual(gate.get('Nova')['needs_you'][0]['id'],'handoff:req-1')
        now[0]=5
        self.assertEqual(gate.get('Nova')['needs_you'],[])
        now[0]=10
        cleared=gate.get('Nova')
        self.assertEqual(cleared['needs_you'],[])
        self.assertEqual(cleared['last_run'],{'outcome':'cancelled','at':60})
        restarted=ActivityGateway(lambda:[{'name':'Nova'}],lambda name:self.snapshot(entries=[{'kind':'message','role':'Nova','text':'Merge the Tesla page fix?','time':9}]))
        self.assertEqual(restarted.get('Nova')['needs_you'],[])

    def test_stale_feed_keeps_a_known_hand_and_does_not_raise_a_new_one(self):
        now=[0];calls=[0]
        def read(name):
            calls[0]+=1
            if calls[0]==1:return self.snapshot(needs_you=[handoff()])
            raise RuntimeError('private provider body')
        gate=ActivityGateway(lambda:[{'name':'Nova'}],read,lambda:now[0])
        self.assertEqual(len(gate.get('Nova')['needs_you']),1)
        now[0]=5
        stale=gate.get('Nova')
        self.assertTrue(stale['stale']);self.assertIsNone(stale['working']);self.assertEqual(len(stale['needs_you']),1)
        self.assertNotIn('private',str(stale))
        failed=ActivityGateway(lambda:[{'name':'Nova'}],lambda name:(_ for _ in ()).throw(RuntimeError('down')))
        self.assertEqual(failed.get('Nova')['needs_you'],[])
        self.assertIsNone(failed.get('Nova')['last_run'])

    def test_run_error_does_not_raise_a_hand_or_treat_cancel_as_failure(self):
        error=ActivityGateway(lambda:[{'name':'Nova'}],lambda name:self.snapshot(last_run={'outcome':'error','at':3},entries=[{'kind':'message','role':'Nova','text':'cancelled?','time':1}])).get('Nova')
        self.assertEqual(error['needs_you'],[])
        self.assertEqual(error['last_run']['outcome'],'error')
        self.assertEqual(attention_priority(needs_you=False,working=False,done=False),'idle')
        self.assertIsNone(ActivityGateway(lambda:[{'name':'Nova'}],lambda name:self.snapshot(working=False)).get('Nova')['last_run'])
