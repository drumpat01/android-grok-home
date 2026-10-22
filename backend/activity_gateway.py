"""Bounded cache of read-only native bot activity; no private bodies in logs."""
import json
import re
import threading
import time
import unicodedata
import urllib.request

PREVIEW_LIMIT=60
ELLIPSIS='\u2026'

def unread_preview(text, limit=PREVIEW_LIMIT):
    """Plain single-line prefix of a bot reply. Empty when there is nothing to show.

    Markdown, links, and code markers are removed. Whitespace collapses. The result
    is at most `limit` Unicode code points and never splits a grapheme cluster.
    Longer text ends on a word boundary with an ellipsis. Full bodies are not returned.
    """
    if not isinstance(text, str) or not text:
        return ''
    cleaned=re.sub(r'\s+', ' ', _strip_markdown(text)).strip()
    if not cleaned:
        return ''
    return _truncate(cleaned, limit)

def _strip_markdown(text):
    text=text.replace('\r\n', '\n').replace('\r', '\n')
    text=re.sub(r'(?m)^[ \t]*(?:`{3}|~{3})[^\n]*$', ' ', text)
    text=re.sub(r'!\[([^\]]*)\]\([^)]*\)', r'\1', text)
    text=re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', text)
    text=re.sub(r'<https?://[^>\s]+>', ' ', text, flags=re.I)
    text=re.sub(r'`+([^`]*)`+', r'\1', text)
    text=re.sub(r'\*\*([^*]+)\*\*', r'\1', text)
    text=re.sub(r'__([^_]+)__', r'\1', text)
    text=re.sub(r'~~([^~]+)~~', r'\1', text)
    text=re.sub(r'(?<!\*)\*(?!\*)([^*]+)\*(?!\*)', r'\1', text)
    text=re.sub(r'(?<!\w)_(?!_)([^_]+)_(?!\w)', r'\1', text)
    text=re.sub(r'<[^>\n]+>', ' ', text)
    text=re.sub(r'https?://\S+', ' ', text, flags=re.I)
    text=re.sub(r'(?m)^[ \t]{0,3}#{1,6}[ \t]*', '', text)
    text=re.sub(r'(?m)^[ \t]{0,3}>[ \t]?', '', text)
    text=re.sub(r'(?m)^[ \t]*([-*_])(?:[ \t]*\1){2,}[ \t]*$', ' ', text)
    text=re.sub(r'(?m)^[ \t]{0,3}(?:[-*+]|\d+[.)])[ \t]+', '', text)
    return text

def _graphemes(text):
    clusters=[]
    buf=''
    for ch in text:
        if buf and not _joins(buf, ch):
            clusters.append(buf)
            buf=ch
        else:
            buf+=ch
    if buf:
        clusters.append(buf)
    return clusters

def _joins(buf, ch):
    if unicodedata.category(ch) in ('Mn', 'Mc', 'Me'):
        return True
    code=ord(ch)
    if code in (0xFE0E, 0xFE0F, 0x20E3) or 0x1F3FB<=code<=0x1F3FF or 0xE0020<=code<=0xE007F:
        return True
    if ch=='\u200d' or buf.endswith('\u200d'):
        return True
    return _regional(ch) and _regional(buf) and len(buf)==1

def _regional(ch):
    return 0x1F1E6<=ord(ch)<=0x1F1FF

def _truncate(text, limit):
    if len(text)<=limit:
        return text
    budget=limit-len(ELLIPSIS)
    taken=0
    prefix=''
    for cluster in _graphemes(text):
        if taken+len(cluster)>budget:
            break
        prefix+=cluster
        taken+=len(cluster)
    prefix=prefix.rstrip(' ')
    if prefix and len(prefix)<len(text) and not text.startswith(prefix+' ') and ' ' in prefix:
        prefix=prefix.rsplit(' ', 1)[0]
    if not prefix:
        return ELLIPSIS
    return prefix+ELLIPSIS

QUESTION_CAP = 600
QUESTION_SHORT_MAX = 25
QUESTION_SHORT_CUT = 24
STALE_HAND_MS = 30 * 60 * 1000
NEEDS_YOU_KINDS = {'approval', 'question', 'error'}
RUN_OUTCOMES = {'complete', 'error', 'cancelled'}

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
    def __init__(self,roster,reader=read_activity,clock=time.monotonic,wall=time.time):
        self.roster,self.reader,self.clock,self.wall=roster,reader,clock,wall
        self.lock=threading.Lock();self.cache={};self.locks={};self.confirmed={}
    def get(self,name):
        if name not in [bot['name'] for bot in self.roster()]:raise ValueError('choose_configured_bot')
        with self.lock: gate=self.locks.setdefault(name,threading.Lock())
        with gate:
            now=self.clock(); cached=self.cache.get(name)
            if cached and now-cached[0]<4:return self._cap_hands(name,cached[1])
            try:
                result=present_activity({**self.reader(name),'stale':False},now_ms=self.wall()*1000)
                self._confirm(name,result);ttl=now
            except Exception:
                if cached:result=present_activity({**cached[1],'stale':True,'working':None,'error':'activity_unavailable'},now_ms=self.wall()*1000)
                else:result=present_activity({'bot':name,'entries':[],'working':None,'stale':True,'error':'activity_unavailable'},now_ms=self.wall()*1000)
                ttl=now+16  # Back off for 20 seconds, including stale reads.
            result=self._cap_hands(name,result)
            self.cache[name]=(ttl,result);return result

    def _confirm(self,name,result):
        now_ms=self.wall()*1000
        self.confirmed[name]={item['id']:now_ms for item in result.get('needs_you') or []}

    def _cap_hands(self,name,result):
        """Drop a cached hand that no poll has confirmed in the last 30 minutes."""
        now_ms=self.wall()*1000
        known=self.confirmed.get(name,{})
        kept=[]
        for item in result.get('needs_you') or []:
            confirmed_at=known.get(item['id'])
            if confirmed_at is not None and now_ms-confirmed_at<=STALE_HAND_MS:kept.append(item)
        result['needs_you']=kept
        return result


def _strip_trailing_punctuation(value):
    end=len(value)
    while end>0:
        ch=value[end-1]
        if ch=='?':break
        if not unicodedata.category(ch).startswith('P'):break
        end-=1
    return value[:end]


def question_short(question, title=''):
    """Native text. Fitting text stays whole. A cut is at most 24 characters."""
    source=(title or '').strip() or (question or '').strip()
    if not source:return '',False
    if len(source)<=QUESTION_SHORT_MAX:return source,False
    words=source.split()
    if len(words[0])>QUESTION_SHORT_CUT:return words[0][:QUESTION_SHORT_CUT],True
    short=words[0]
    for word in words[1:]:
        candidate=short+' '+word
        if len(candidate)>QUESTION_SHORT_CUT:break
        short=candidate
    return _strip_trailing_punctuation(short),True


def _latest_you(entries):
    if not isinstance(entries,list):return None
    times=[row.get('time') for row in entries if isinstance(row,dict) and row.get('kind')=='message' and row.get('role')=='You' and isinstance(row.get('time'),(int,float))]
    return max(times) if times else None


def _options(value):
    if not isinstance(value,list):return []
    options=[]
    for option in value[:4]:
        if not isinstance(option,dict):continue
        ident,label=option.get('id'),option.get('label')
        if not isinstance(ident,str) or not ident.strip() or not isinstance(label,str) or not label.strip():continue
        options.append({'id':ident.strip()[:40],'label':label.strip()[:24],'recommended':option.get('recommended') is True,'confirm':option.get('confirm') is True})
    seen=False
    for option in options:
        if option['recommended'] and seen:option['recommended']=False
        elif option['recommended']:seen=True
    return options


def normalize_needs_you(items, entries, now_ms):
    """Keep structured items only. Message text never becomes an item."""
    if not isinstance(items,list):return []
    latest_you=_latest_you(entries)
    kept=[]
    for item in items:
        if not isinstance(item,dict) or item.get('kind') not in NEEDS_YOU_KINDS:continue
        ident=item.get('id')
        if not isinstance(ident,str) or not ident.strip():continue
        question=item.get('question').strip()[:QUESTION_CAP] if isinstance(item.get('question'),str) else ''
        title=item.get('title').strip()[:80] if isinstance(item.get('title'),str) else ''
        raw_created=item.get('created')
        created=int(raw_created) if isinstance(raw_created,(int,float)) and raw_created>0 else int(now_ms)
        expires=int(item['expires']) if isinstance(item.get('expires'),(int,float)) else None
        if expires is not None and expires<=now_ms:continue
        if latest_you is not None and created<latest_you:continue
        answerable=item.get('kind')!='approval' and item.get('answerable_here') is True
        context=item.get('context').strip()[:80] if isinstance(item.get('context'),str) else ''
        short,truncated=question_short(question,title)
        normalized={'id':ident.strip()[:200],'kind':item['kind'],'question':question,'question_short':short,
                    'question_short_truncated':truncated,'context':context,'options':_options(item.get('options')) if answerable else [],
                    'created':created,'expires':expires,'answerable_here':answerable}
        if title:normalized['title']=title
        kept.append(normalized)
    return kept


def normalize_last_run(value):
    if not isinstance(value,dict) or value.get('outcome') not in RUN_OUTCOMES or not isinstance(value.get('at'),(int,float)):return None
    return {'outcome':value['outcome'],'at':int(value['at'])}


def present_activity(snapshot, now_ms=None):
    shown=display_snapshot(snapshot)
    if now_ms is None:now_ms=time.time()*1000
    return {**shown,'needs_you':normalize_needs_you(shown.get('needs_you'),shown.get('entries'),now_ms),'last_run':normalize_last_run(shown.get('last_run'))}


def attention_priority(*, needs_you, working, done=False, stale=False):
    """Spec 1.4 before tablet snooze and motion. Needs you outranks done, working, and idle."""
    if needs_you:return 'needs_you'
    if stale or (not done and not working):return 'idle'
    if done:return 'done'
    if working:return 'working'
    return 'idle'


def activity_summary(snapshot):
    """Unread markers follow the newest completed assistant reply.

    latestUnreadPreview is a short plain-text prefix of that reply. needs_you and
    last_run come from the same snapshot. Full message bodies, user text, contacts,
    and tool payloads stay out of the summary.
    """
    snapshot=present_activity(snapshot)
    name=snapshot.get('bot','')
    latest=next((row for row in reversed(snapshot.get('entries',[]))
                 if row.get('kind')=='message' and row.get('role')==name),{})
    body=latest.get('text','')
    return {'bot':name,'stale':snapshot.get('stale',True),'working':snapshot.get('working'),
            'reply_id':latest.get('id',''),'reply_revision':str(latest.get('updatedSeq',latest.get('seq','0'))),
            'reply_seq':str(latest.get('seq','0')),'reply_time':latest.get('time',0),
            'latestUnreadPreview':unread_preview(body),'needs_you':snapshot['needs_you'],'last_run':snapshot['last_run']}


def display_snapshot(snapshot):
    """Keep coordination protocol out of the visible native conversation."""
    rows=[];routing=False
    try:
        from bot_gateway import BRIEF_HINT
    except Exception:
        BRIEF_HINT=''
    for original in snapshot.get('entries',[]):
        row=dict(original);text=row.get('text','')
        if BRIEF_HINT and row.get('kind')=='message' and row.get('role')=='You' and isinstance(text,str) and text.endswith(BRIEF_HINT):
            text=text[:-len(BRIEF_HINT)];row['text']=text
        if row.get('kind')=='message' and row.get('role')=='You':
            routing=text.startswith('Choose the best existing bot for the user request below, based on your knowledge of the team. ') and '\nUSER REQUEST:\n' in text
            if routing:row['text']='Who can help? '+text.split('\nUSER REQUEST:\n',1)[1]
        elif routing and row.get('kind')=='message' and row.get('role')==snapshot.get('bot') and text.strip().startswith('ROUTE: '):
            row['text']='Recommended bot: '+text.strip()[7:];routing=False
        rows.append(row)
    return {**snapshot,'entries':rows}
