import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { STAGE_ROSTER } from './stage-roster.mjs';

// The roster changes often, so this checks that the stage matches the connector roster, not a fixed list.
test('the stage roster matches bots.local.json in the same order, with no duplicates', () => {
  const configured = JSON.parse(readFileSync(new URL('./bots.local.json', import.meta.url), 'utf8')).map((bot) => bot.name);
  assert.deepEqual(STAGE_ROSTER, configured);
  assert.equal(new Set(STAGE_ROSTER.map((name) => name.toLowerCase())).size, STAGE_ROSTER.length);
});
