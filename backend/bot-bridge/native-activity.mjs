import { randomUUID } from 'node:crypto';

const METHODS = new Set(['ListGrokBotAgents','ListGrokBotAgentStatuses','ListGrokBotTranscriptEntries']);
const short = (value, max=4000) => typeof value === 'string' ? value.slice(0,max) : '';
const sequence = value => /^\d{1,30}$/.test(String(value ?? '')) ? String(value) : '0';
export function normalizeEntries(entries, owner, roster) {
  const seen = new Map();
  const peer = ref => {
    if (!ref || typeof ref !== 'object' || (ref.kind && !['agent','cloud-agent'].includes(ref.kind))) return null;
    const known = roster.find(bot => ref.id && [bot.id,bot.agentId,bot.legacyAgentId].includes(ref.id));
    return short(known?.name || ref.name, 255) || null;
  };
  for (const entry of entries) {
    if (typeof entry.body !== 'string' || entry.body.length > 1500000) continue;
    let body; try { body=JSON.parse(Buffer.from(entry.body,'base64').toString('utf8')); } catch { continue; }
    if (!body || body.suppressed || body.isStreaming || body.channel) continue;
    const id=short(entry.entryId || body.id,512); if (!id) continue;
    let row={id,seq:sequence(entry.seq),updatedSeq:sequence(entry.updatedSeq ?? entry.seq),time:Number(body.timestampMs)||0};
    if (body.kind==='message' && (body.toAgent || body.fromAgent)) {
      const target=peer(body.toAgent), source=peer(body.fromAgent);
      if (body.toAgent && !target || body.fromAgent && !source) continue;
      row={...row,kind:'contact',from:source || owner,to:target || owner,text:short(body.content)};
    } else if (body.kind==='send-message' && body.message?.type==='text') {
      if(body.message.channel)continue;
      row={...row,kind:'message',role:owner,text:short(body.message.content,12000)};
    } else if (body.kind==='message' && body.role==='user') {
      row={...row,kind:'message',role:'You',text:short(body.content,12000)};
    } else continue;
    if(row.kind==='message' && !row.text.trim())continue;
    const old=seen.get(id);
    if(!old || BigInt(row.updatedSeq)>BigInt(old.updatedSeq))seen.set(id,row);
  }
  let budget=180000;
  return [...seen.values()].sort((a,b)=>a.time-b.time || (BigInt(a.seq)<BigInt(b.seq)?-1:BigInt(a.seq)>BigInt(b.seq)?1:0)).slice(-150).reverse().map(row=>{const text=row.text.slice(0,Math.max(0,budget));budget-=text.length;return {...row,text};}).reverse();
}

export class NativeActivity {
  constructor({credential,exchange,fetcher=fetch,clock=Date.now}) {
    this.credential=credential;this.exchange=exchange;this.fetcher=fetcher;this.clock=clock;
    this.roster=null;this.rosterAt=0;this.token=null;this.key=null;this.authFlight=null;
  }
  async auth() {
    const key=await this.credential();if(!key)throw new Error('cursor_sign_in_required');
    if(this.key===key && this.token && this.token.expiresAtMs>this.clock()+60000)return this.token.token;
    if(!this.authFlight)this.authFlight=this.exchange(key).then(token=>{this.key=key;this.token=token;return token.token;}).finally(()=>{this.authFlight=null;});
    return this.authFlight;
  }
  async read(method,args) {
    if(!METHODS.has(method))throw new Error('read_method_required');
    const token=await this.auth();
    const response=await this.fetcher(`https://api2.cursor.sh/aiserver.v1.GrokBotService/${method}`,{method:'POST',headers:{authorization:`Bearer ${token}`,'content-type':'application/json','connect-protocol-version':'1','x-cursor-client-type':'agent-serve','x-request-id':randomUUID()},body:JSON.stringify(args),signal:AbortSignal.timeout(20000)});
    if(!response.ok){if(response.status===401)this.token=null;throw new Error(response.status===429?'activity_rate_limited':response.status===401||response.status===403?'activity_auth_required':'activity_unavailable');}
    return response.json();
  }
  async snapshot(name,allowlist) {
    if(!allowlist.includes(name))throw new Error('choose_configured_bot');
    if(!this.roster || this.clock()-this.rosterAt>60000){const result=await this.read('ListGrokBotAgents',{});if(!Array.isArray(result.agents))throw new Error('activity_schema_changed');this.roster=result.agents;this.rosterAt=this.clock();}
    const bot=this.roster.find(bot=>bot.name===name);if(!bot)throw new Error('bot_not_found');
    const id=bot.agentId || bot.id;
    const [page,state]=await Promise.all([
      this.read('ListGrokBotTranscriptEntries',{agentId:id,sessionId:bot.viewerSessionId || '',limit:200}),
      this.read('ListGrokBotAgentStatuses',{agentIds:[id]}),
    ]);
    if(!Array.isArray(page.entries))throw new Error('activity_schema_changed');
    const status=(state.statuses ?? []).find(item=>item.agentId===id);
    return {bot:name,source:'native',synced_at:this.clock(),working:status?status.isRunning===true:null,entries:normalizeEntries(page.entries,name,this.roster)};
  }
}
