import test from 'node:test';
import assert from 'node:assert/strict';
import { NativeActivity,normalizeEntries } from './native-activity.mjs';
const roster=[{name:'Max',id:'max'},{name:'Nova',id:'kit'}];
const row=(id,body,updated='1')=>({entryId:id,seq:'1',updatedSeq:updated,body:Buffer.from(JSON.stringify(body)).toString('base64')});
test('only structured bot references produce contact rows; mentions remain messages',()=>{
 const entries=[row('1',{kind:'message',fromAgent:{id:'max',name:'wrong'},content:'Hi',timestampMs:10}),row('2',{kind:'message',toAgent:{id:'max'},content:'Reply',timestampMs:20}),row('3',{kind:'send-message',message:{type:'text',content:'I mentioned Max'},timestampMs:30})];
 const result=normalizeEntries(entries,'Nova',roster);
 assert.deepEqual(result.map(item=>item.kind),['contact','contact','message']);
 assert.equal(result[0].from,'Max');assert.equal(result[0].to,'Nova');assert.equal(result[1].to,'Max');assert.equal(result[2].role,'Nova');
});
test('deduplicates revisions, omits suppressed/partial/meeting/person/tool rows',()=>{
 const entries=[row('same',{kind:'message',role:'user',content:'old'}),row('same',{kind:'message',role:'user',content:'new'},'2'),row('a',{kind:'message',fromAgent:{name:'Pat',kind:'person'}}),row('b',{kind:'tool-call',content:'private work'}),row('c',{kind:'message',role:'user',content:'hidden',suppressed:true}),row('d',{kind:'message',role:'user',content:'partial',isStreaming:true}),row('e',{kind:'message',role:'user',content:'meeting',channel:'meeting:1'})];
 assert.deepEqual(normalizeEntries(entries,'Nova',roster).map(item=>item.text),['new']);
});
test('native adapter restricts reads, shares token and rejects unknown bots before network',async()=>{
 let exchanges=0;const calls=[];
 const client=new NativeActivity({credential:()=> 'test-key',exchange:async()=>{exchanges++;return {token:'test-token',expiresAtMs:Date.now()+3600000};},fetcher:async(url,options)=>{
  calls.push(url.split('/').at(-1));assert.equal(options.method,'POST');
  const data=url.endsWith('ListGrokBotAgents')?{agents:roster}:url.endsWith('ListGrokBotAgentStatuses')?{statuses:[{agentId:'kit',isRunning:true}]}:{entries:[]};return {ok:true,json:async()=>data};
 }});
 await assert.rejects(client.snapshot('Typo',['Nova']),/choose_configured_bot/);assert.equal(calls.length,0);
 const result=await client.snapshot('Nova',['Nova']);assert.equal(result.working,true);assert.equal(exchanges,1);
 await assert.rejects(client.read('SendGrokBotAgentMessage',{}),/read_method_required/);
 assert(calls.every(method=>method.startsWith('List')));
});
test('missing live status stays unknown and provider errors do not leak bodies',async()=>{
 const client=new NativeActivity({credential:()=> 'secret',exchange:async()=>({token:'token',expiresAtMs:Date.now()+3600000}),fetcher:async()=>({ok:false,status:403})});
 await assert.rejects(client.snapshot('Nova',['Nova']),/^Error: activity_auth_required$/);
});
