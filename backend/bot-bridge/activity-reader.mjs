import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { pathToFileURL } from 'node:url';
import { configuredBots } from './config.mjs';

// Independent read-only transcript access. Never resolve/create sessions, send,
// interrupt, or advance grokbot__check's cursor. SDK internals are version-pinned.
export class ActivityReader {
  constructor(contacts, { fetcher = fetch, credential, baseUrl = 'https://api.cursor.com' } = {}) {
    if (baseUrl !== 'https://api.cursor.com') throw new Error('Unexpected Cursor API host');
    this.contacts = new Map(contacts.map(item => [item.name, item.sessionId]));
    this.fetcher = fetcher; this.credential = credential; this.baseUrl = baseUrl;
  }
  async read(name, after = '-1') {
    const id = this.contacts.get(name);
    if (typeof id !== 'string' || !id) throw new Error('Existing bot session required; no bot was created');
    if (typeof after !== 'string' || after.length > 128) throw new Error('Invalid transcript cursor');
    const key = await this.credential();
    if (!key) throw new Error('Cursor sign-in required');
    const response = await this.fetcher(`${this.baseUrl}/v0/grokbot/sessions/${encodeURIComponent(id)}/entries?afterUpdatedSeq=${encodeURIComponent(after)}`, {
      method: 'GET', headers: { authorization: `Bearer ${key}`, 'x-cursor-client-type': 'agent-serve', 'x-request-id': randomUUID() },
      signal: AbortSignal.timeout(30000),
    });
    // Do not include response bodies or credentials in errors/logs.
    if (!response.ok) throw new Error(`Transcript read failed (HTTP ${response.status})`);
    const data = await response.json();
    if (!Array.isArray(data.entries) || typeof data.latestUpdatedSeq !== 'string') throw new Error('Unexpected transcript format');
    return { bot: name, cursor: data.latestUpdatedSeq, turn: data.turn ?? null, entries: data.entries };
  }
}

// Diagnostics expose field names and event kinds, never conversation text or IDs.
export function describePage(page) {
  const kinds = {}, fields = new Set(), eventTextFields = new Set();
  let structuredEvents = 0, plainEvents = 0;
  function walk(value, prefix, depth) {
    if (!value || typeof value !== 'object' || depth > 5) return;
    for (const key of Object.keys(value)) {
      if (Array.isArray(value)) { for (const item of value.slice(0, 5)) walk(item, prefix + '[]', depth + 1); break; }
      const path = prefix ? prefix + '.' + key : key; fields.add(path);
      walk(value[key], path, depth + 1);
    }
  }
  for (const entry of page.entries) {
    const kind = typeof entry.kind === 'string' ? entry.kind : 'unknown';
    kinds[kind] = (kinds[kind] ?? 0) + 1; walk(entry, '', 0);
    if (kind === 'event' && typeof entry.text === 'string') {
      try {
        const value = JSON.parse(entry.text);
        if (value && typeof value === 'object') { structuredEvents++; for (const key of Object.keys(value)) eventTextFields.add(key); }
        else plainEvents++;
      } catch { plainEvents++; }
    }
  }
  return { bot: page.bot, count: page.entries.length, kinds, fields: [...fields].sort(),
    eventText: { structured: structuredEvents, plain: plainEvents, fields: [...eventTextFields].sort() },
    turn: { idle: page.turn?.idle === true, inFlight: page.turn?.inFlight === true, queued: typeof page.turn?.queued === 'number' ? page.turn.queued : null } };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const name = process.argv[2];
    if (!configuredBots().some(bot => bot.name === name)) throw new Error('Choose a configured bot by exact name');
    const contacts = JSON.parse(readFileSync(new URL('../../artifacts/roster-connections.json', import.meta.url), 'utf8'));
    const { resolveApiKeySync } = await import('./node_modules/@cursor/bdk/dist/internal/cursor/credentials.js');
    const reader = new ActivityReader(contacts, { credential: () => resolveApiKeySync({})?.apiKey });
    let cursor = '-1'; const summaries = [];
    for (let page = 0; page < 10; page++) {
      const result = await reader.read(name, cursor); summaries.push(describePage(result));
      if (!result.entries.length || result.cursor === cursor) break;
      cursor = result.cursor;
    }
    console.log(JSON.stringify({ source: 'read-only-existing-session', pages: summaries }, null, 2));
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
