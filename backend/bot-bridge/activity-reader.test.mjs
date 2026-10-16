import test from 'node:test';
import assert from 'node:assert/strict';
import { ActivityReader, describePage } from './activity-reader.mjs';

test('read-only connector preserves metadata and uses its own supplied cursor', async () => {
  const entries = [{ kind: 'event', text: 'private content', message: { type: 'activity', recipient: { name: 'Nova' } } }];
  let request;
  const reader = new ActivityReader([{ name: 'Max', sessionId: 'existing/id' }], {
    credential: () => 'test-only', fetcher: async (url, options) => {
      request = { url, options }; return { ok: true, json: async () => ({ entries, latestUpdatedSeq: '42', turn: { inFlight: true } }) };
    },
  });
  const page = await reader.read('Max', '17');
  assert.equal(request.options.method, 'GET'); assert.match(request.url, /existing%2Fid\/entries\?afterUpdatedSeq=17$/);
  assert.deepEqual(page.entries, entries);
  const summary = describePage(page);
  assert(summary.fields.includes('message.recipient.name')); assert.equal(summary.turn.inFlight, true);
  assert(!JSON.stringify(summary).includes('private content')); assert(!JSON.stringify(summary).includes('test-only'));
});
test('unknown bots cannot trigger session creation or any network request', async () => {
  let calls = 0;
  const reader = new ActivityReader([], { credential: () => 'test', fetcher: () => { calls++; } });
  await assert.rejects(reader.read('Typo'), /Existing bot session required/); assert.equal(calls, 0);
});
test('HTTP error does not expose response body or credentials', async () => {
  const reader = new ActivityReader([{ name: 'Nova', sessionId: 'known' }], { credential: () => 'secret', fetcher: async () => ({ ok: false, status: 403 }) });
  await assert.rejects(reader.read('Nova'), /^Error: Transcript read failed \(HTTP 403\)$/);
});
