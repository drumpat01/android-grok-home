import test from 'node:test';
import assert from 'node:assert/strict';
import { NativeActivity } from './native-activity.mjs';
import { dropAnswered, lastRunFromStatus, needsYouFromStatus, questionShort } from './needs-you.mjs';

const handoff = {
  agentId: 'kit', isRunning: true, updatedAtMs: 50,
  boxHandoffRequestId: 'req-1',
  boxHandoffInstruction: 'Merge the Tesla page fix?',
  awaiting: {reason: 'website', sinceMs: 40, tabId: 'desktop'},
};

test('question text is not a hand, including a question mark', () => {
  assert.deepEqual(needsYouFromStatus({isRunning: false}), []);
  assert.deepEqual(needsYouFromStatus({isRunning: true, awaiting: {reason: 'Are you there?'}}), []);
  assert.equal(lastRunFromStatus({isRunning: false}), null);
  assert.equal(lastRunFromStatus({lastRun: {outcome: 'cancelled', at: 9}}).outcome, 'cancelled');
});

test('a native handoff is needs_you and is not answerable here', () => {
  const [item] = needsYouFromStatus(handoff);
  assert.equal(item.kind, 'approval');
  assert.equal(item.answerable_here, false);
  assert.deepEqual(item.options, []);
  assert.equal(item.question, 'Merge the Tesla page fix?');
  assert.equal(item.question_short, 'Merge the Tesla page fix?');
  assert.equal(item.question_short_truncated, false);
  assert.equal(item.context, 'website');
  assert.equal(item.created, 40);
  assert.equal(item.id, 'handoff:req-1');
});

test('short form keeps fitting text, including punctuation, and flags it uncut', () => {
  const exact = questionShort('Merge the Tesla page fix?');
  assert.equal(exact.text, 'Merge the Tesla page fix?');
  assert.equal(exact.text.length, 25);
  assert.equal(exact.truncated, false);
  assert.deepEqual(questionShort('the Tesla page?'), {text: 'the Tesla page?', truncated: false});
  assert.deepEqual(questionShort('Ship it!'), {text: 'Ship it!', truncated: false});
  assert.deepEqual(questionShort('', ''), {text: '', truncated: false});
  const titled = needsYouFromStatus({...handoff, boxHandoffTitle: 'Merge fix?'})[0];
  assert.equal(titled.question, 'Merge the Tesla page fix?');
  assert.equal(titled.question_short, 'Merge fix?');
  assert.equal(titled.question_short_truncated, false);
  assert.equal(titled.title, 'Merge fix?');
});

test('a cut stops at 24 characters on a word boundary and strips trailing punctuation except ?', () => {
  const full = 'Merge the Tesla page fix now please?';
  const short = questionShort(full);
  assert.equal(short.text, 'Merge the Tesla page fix');
  assert.equal(short.text.length, 24);
  assert.equal(short.truncated, true);
  assert.ok(full.startsWith(short.text));
  assert.equal(questionShort('the Tesla page needs a longer instruction today please').text.startsWith('the '), true);
  const bang = questionShort(`Ship it! ${'y'.repeat(30)}`);
  assert.equal(bang.text, 'Ship it');
  assert.equal(bang.truncated, true);
  const asked = questionShort(`Go? ${'x'.repeat(30)}`);
  assert.equal(asked.text, 'Go?');
  assert.equal(asked.truncated, true);
  const comma = questionShort('Approve, immediately, something longer');
  assert.equal(comma.text, 'Approve, immediately');
  assert.equal(comma.truncated, true);
  assert.ok('Approve, immediately, something longer'.startsWith(`${comma.text},`));
});

test('a single word over 24 characters keeps its first 24 and is marked truncated', () => {
  const mixed = `${'A'.repeat(22)}!!${'B'.repeat(20)}`;
  const short = questionShort(mixed);
  assert.equal(short.text, mixed.slice(0, 24));
  assert.equal(short.text.length, 24);
  assert.equal(short.truncated, true);
  const marks = '!'.repeat(40);
  const punctuated = questionShort(marks);
  assert.equal(punctuated.text, '!'.repeat(24));
  assert.equal(punctuated.text.length, 24);
  assert.notEqual(punctuated.text, '');
  assert.equal(punctuated.truncated, true);
  assert.equal(questionShort('A'.repeat(30)).text, 'A'.repeat(24));
  assert.equal(questionShort('A'.repeat(30)).truncated, true);
  const titled = questionShort('Merge the Tesla page fix?', 'A'.repeat(30));
  assert.equal(titled.text, 'A'.repeat(24));
  assert.equal(titled.truncated, true);
});

test('an empty native instruction stays empty', () => {
  const [item] = needsYouFromStatus({boxHandoffRequestId: 'req-2', boxHandoffInstruction: '  '});
  assert.equal(item.question, '');
  assert.equal(item.question_short, '');
  assert.equal(item.question_short_truncated, false);
  assert.equal(item.answerable_here, false);
});

test('a hand with no native timestamp uses the poll time, so an older You message stays', () => {
  const [item] = needsYouFromStatus({boxHandoffRequestId: 'req-9', boxHandoffInstruction: 'Merge the Tesla page fix?'}, 5000);
  assert.equal(item.created, 5000);
  const earlier = [{kind: 'message', role: 'You', time: 100, text: 'Noted'}];
  assert.equal(dropAnswered([item], earlier, 5000).length, 1);
  assert.equal(dropAnswered([{...item, created: 0}], earlier, 5000).length, 0);
  assert.equal(dropAnswered([item], [{kind: 'message', role: 'You', time: 5001}], 5000).length, 0);
});

test('a newer You message or a cleared handoff drops the item; a failure does not raise a hand', () => {
  const [item] = needsYouFromStatus(handoff);
  assert.equal(dropAnswered([item], [{kind: 'message', role: 'You', time: 30}]).length, 1);
  assert.equal(dropAnswered([item], [{kind: 'message', role: 'You', time: 41, text: 'Approved'}]).length, 0);
  assert.equal(dropAnswered([{...item, expires: 10}], [], 11).length, 0);
  assert.deepEqual(needsYouFromStatus({isRunning: false, lastRun: {outcome: 'error', at: 8}}), []);
  assert.deepEqual(needsYouFromStatus({isRunning: false, lastRun: {outcome: 'cancelled', at: 8}}), []);
});

test('snapshot ignores reply text and reports a handoff only from status', async () => {
  const row = (body) => ({entryId: 'e', seq: '2', updatedSeq: '2', body: Buffer.from(JSON.stringify(body)).toString('base64')});
  const entries = [row({kind: 'send-message', message: {type: 'text', content: 'Should I merge the Tesla page fix?'}, timestampMs: 5})];
  const client = new NativeActivity({
    credential: () => 'test-key',
    exchange: async () => ({token: 'token', expiresAtMs: Date.now() + 3600000}),
    fetcher: async (url) => ({ok: true, json: async () => url.endsWith('ListGrokBotAgents') ? {agents: [{name: 'Nova', id: 'kit'}]} : url.endsWith('ListGrokBotAgentStatuses') ? {statuses: [{agentId: 'kit', isRunning: false}]} : {entries}}),
  });
  const quiet = await client.snapshot('Nova', ['Nova']);
  assert.deepEqual(quiet.needs_you, []);
  assert.equal(quiet.last_run, null);
  assert.match(quiet.entries.at(-1).text, /\?$/);
  client.roster = null;
  client.fetcher = async (url) => ({ok: true, json: async () => url.endsWith('ListGrokBotAgents') ? {agents: [{name: 'Nova', id: 'kit'}]} : url.endsWith('ListGrokBotAgentStatuses') ? {statuses: [{...handoff, agentId: 'kit'}]} : {entries: []}});
  const waiting = await client.snapshot('Nova', ['Nova']);
  assert.equal(waiting.needs_you.length, 1);
  assert.equal(waiting.working, true);
  assert.equal(waiting.needs_you[0].answerable_here, false);
  assert.equal(waiting.needs_you[0].question_short_truncated, false);
});

test('snapshot uses the poll clock when the handoff has no timestamp', async () => {
  const row = (body) => ({entryId: 'e', seq: '2', updatedSeq: '2', body: Buffer.from(JSON.stringify(body)).toString('base64')});
  const you = row({kind: 'message', role: 'user', content: 'Earlier note', timestampMs: 100});
  const client = new NativeActivity({
    credential: () => 'test-key',
    exchange: async () => ({token: 'token', expiresAtMs: 9e15}),
    clock: () => 5000,
    fetcher: async (url) => ({ok: true, json: async () => url.endsWith('ListGrokBotAgents') ? {agents: [{name: 'Nova', id: 'kit'}]} : url.endsWith('ListGrokBotAgentStatuses') ? {statuses: [{agentId: 'kit', isRunning: true, boxHandoffRequestId: 'req-9', boxHandoffInstruction: 'Merge the Tesla page fix?'}]} : {entries: [you]}}),
  });
  const waiting = await client.snapshot('Nova', ['Nova']);
  assert.equal(waiting.needs_you.length, 1);
  assert.equal(waiting.needs_you[0].created, 5000);
});
