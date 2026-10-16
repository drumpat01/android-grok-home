import test from 'node:test';
import assert from 'node:assert/strict';
import { BotBridge } from './client.mjs';
import { parseBots } from './config.mjs';

const bots = [{ name: 'Test Existing Bot', color: '#FFFFFF' }];
test('unknown names and empty messages cannot reach Cursor', async () => {
  const bridge = new BotBridge(bots, { fetcher: () => assert.fail('must not send') });
  await assert.rejects(bridge.call('ask', 'Wrong Bot', 'Hello'), /exact name/);
  await assert.rejects(bridge.call('ask', bots[0].name, ' '), /Message/);
  await assert.rejects(bridge.call('interrupt', bots[0].name), /Unsupported/);
});
test('calls official tool directly and keeps old replies separate', async () => {
  let calls = 0;
  const bridge = new BotBridge(bots, { fetcher: async (url, options) => {
    calls++;
    assert.equal(url, 'http://127.0.0.1:8790/v1/tools/grokbot__ask');
    assert.deepEqual(JSON.parse(options.body), { input: { agent: bots[0].name, wait_seconds: 45, message: 'Hello' } });
    return { ok: true, json: async () => ({ ok: true, result: { status: 'running', reply: 'Current answer', earlierReplies: ['Older answer'] } }) };
  } });
  const result = await bridge.call('ask', bots[0].name, 'Hello');
  assert.equal(result.reply, 'Current answer');
  assert.deepEqual(result.earlierReplies, ['Older answer']);
  assert.equal(calls, 1);
});
test('network failure does not resend the user message', async () => {
  let calls = 0;
  const bridge = new BotBridge(bots, { fetcher: async () => { calls++; throw new Error('disconnected'); } });
  await assert.rejects(bridge.call('ask', bots[0].name, 'Hello'), /disconnected/);
  assert.equal(calls, 1);
  assert.equal(bridge.pending.size, 0);
});
test('newly created bot is reported instead of treated as the existing bot', async () => {
  const bridge = new BotBridge(bots, { fetcher: async () => ({ ok: true, json: async () => ({ ok: true, result: { created: true } }) }) });
  await assert.rejects(bridge.call('ask', bots[0].name, 'Hello'), /created an empty bot/);
});
test('rejects duplicate config and nonlocal BDK hosts', () => {
  assert.throws(() => parseBots([{ name: 'A' }, { name: 'a' }]), /unique/);
  assert.throws(() => new BotBridge(bots, { baseUrl: 'http://example.com' }), /loopback/);
});
test('parallel calls cannot mix replies for the same bot', async () => {
  let finish;
  const bridge = new BotBridge(bots, { fetcher: () => new Promise(resolve => { finish = resolve; }) });
  const first = bridge.call('ask', bots[0].name, 'Hello');
  await assert.rejects(bridge.call('check', bots[0].name), /already pending/);
  finish({ ok: true, json: async () => ({ ok: true, result: { status: 'finished', reply: 'Hello' } }) });
  await first;
});
