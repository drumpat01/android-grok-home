import { readFileSync } from 'node:fs';

export function parseBots(value) {
  if (!Array.isArray(value)) throw new Error('Bot configuration must be an array.');
  const seen = new Set();
  return value.map(bot => {
    if (!bot || typeof bot.name !== 'string' || !bot.name.trim() || bot.name.length > 255) {
      throw new Error('Each bot needs its exact Grok Bot name.');
    }
    const name = bot.name.trim();
    const key = name.toLowerCase();
    if (seen.has(key)) throw new Error('Bot names must be unique.');
    seen.add(key);
    return { name, color: /^#[0-9a-f]{6}$/i.test(bot.color ?? '') ? bot.color : '#FFFFFF' };
  });
}

export function configuredBots() {
  try {
    return parseBots(JSON.parse(readFileSync(new URL('./bots.local.json', import.meta.url), 'utf8')));
  } catch (error) {
    if (error.code === 'ENOENT') return [];
    throw error;
  }
}
