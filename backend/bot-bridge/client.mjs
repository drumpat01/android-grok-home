import { pathToFileURL } from 'node:url';
import { configuredBots } from './config.mjs';

export class BotBridge {
  constructor(bots, { baseUrl = 'http://127.0.0.1:8790', fetcher = fetch } = {}) {
    const url = new URL(baseUrl);
    if (url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
      throw new Error('BDK must use a local loopback address.');
    }
    this.bots = bots;
    this.baseUrl = url.origin;
    this.fetcher = fetcher;
    this.pending = new Set();
  }

  async call(action, name, message) {
    if (!['list', 'ask', 'check'].includes(action)) throw new Error('Unsupported bridge action.');
    const input = {};
    if (action !== 'list') {
      if (!this.bots.some(bot => bot.name === name)) throw new Error('Select a configured bot by its exact name.');
      input.agent = name;
      input.wait_seconds = 45;
    }
    if (action === 'ask') {
      if (typeof message !== 'string' || !message.trim() || message.length > 8000) throw new Error('Message must contain 1 to 8000 characters.');
      input.message = message;
    }
    if (name && this.pending.has(name)) throw new Error('A request to this bot is already pending.');
    if (name) this.pending.add(name);
    try {
      const response = await this.fetcher(`${this.baseUrl}/v1/tools/grokbot__${action}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ input }), signal: AbortSignal.timeout(55000),
      });
      if (!response.ok) throw new Error(`BDK request failed (HTTP ${response.status}); no automatic retry was sent.`);
      const result = await response.json();
      if (!result.ok || result.isError) throw new Error('BDK could not complete the request. Check account sign-in, bot status, and local BDK diagnostics.');
      if (result.result?.created) throw new Error('Cursor created an empty bot because the name did not match. Stop and correct the configured name before sending again.');
      return result.result;
    } finally {
      if (name) this.pending.delete(name);
    }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const [action = 'list', name, ...words] = process.argv.slice(2);
    const bridge = new BotBridge(configuredBots());
    console.log(JSON.stringify(await bridge.call(action, name, words.join(' ')), null, 2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
