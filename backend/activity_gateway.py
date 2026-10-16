"""Bounded cache of read-only native bot activity; no private bodies in logs."""
import json
import threading
import time
import urllib.request

def read_activity(name):
    request=urllib.request.Request('http://127.0.0.1:8790/v1/tools/native_activity',
        data=json.dumps({'input':{'agent':name}}).encode(),headers={'Content-Type':'application/json'},method='POST')
    with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(request,timeout=30) as response:
        data=json.load(response)
    if not data.get('ok') or data.get('isError') or not isinstance(data.get('result'),dict):
        raise ValueError('activity_unavailable')
    result=data['result']
    if result.get('bot')!=name or not isinstance(result.get('entries'),list):raise ValueError('activity_unavailable')
    return result

class ActivityGateway:
    def __init__(self,roster,reader=read_activity,clock=time.monotonic):
        self.roster,self.reader,self.clock=roster,reader,clock
        self.lock=threading.Lock();self.cache={};self.locks={}
    def get(self,name):
        if name not in [bot['name'] for bot in self.roster()]:raise ValueError('choose_configured_bot')
        with self.lock: gate=self.locks.setdefault(name,threading.Lock())
        with gate:
            now=self.clock(); cached=self.cache.get(name)
            if cached and now-cached[0]<4:return cached[1]
            try: result=display_snapshot({**self.reader(name),'stale':False});ttl=now
            except Exception:
                if cached:result={**cached[1],'stale':True,'working':None,'error':'activity_unavailable'}
                else:result={'bot':name,'entries':[],'working':None,'stale':True,'error':'activity_unavailable'}
                ttl=now+16  # Back off for 20 seconds, including stale reads.
            self.cache[name]=(ttl,result);return result


def activity_summary(snapshot):
    """Only completed assistant replies drive unread state; never expose message bodies."""
    name=snapshot.get('bot','')
    latest=next((row for row in reversed(snapshot.get('entries',[]))
                 if row.get('kind')=='message' and row.get('role')==name),{})
    return {'bot':name,'stale':snapshot.get('stale',True),'working':snapshot.get('working'),
            'reply_id':latest.get('id',''),'reply_revision':str(latest.get('updatedSeq',latest.get('seq','0'))),
            'reply_seq':str(latest.get('seq','0')),'reply_time':latest.get('time',0)}


def display_snapshot(snapshot):
    """Keep coordination protocol out of the visible native conversation."""
    rows=[];routing=False
    for original in snapshot.get('entries',[]):
        row=dict(original);text=row.get('text','')
        if row.get('kind')=='message' and row.get('role')=='You':
            routing=text.startswith('Choose the best existing bot for the user request below, based on your knowledge of the team. ') and '\nUSER REQUEST:\n' in text
            if routing:row['text']='Who can help? '+text.split('\nUSER REQUEST:\n',1)[1]
        elif routing and row.get('kind')=='message' and row.get('role')==snapshot.get('bot') and text.strip().startswith('ROUTE: '):
            row['text']='Recommended bot: '+text.strip()[7:];routing=False
        rows.append(row)
    return {**snapshot,'entries':rows}
