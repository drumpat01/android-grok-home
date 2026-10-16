import cursorGrokBotAgents from '@cursor/bdk/extensions/cursor-grokbot-agents';
import { configuredBots } from '../../config.mjs';

const bots = configuredBots();
if (!bots.length) throw new Error('Add an existing bot by exact name in bots.local.json before starting the bridge.');

export default cursorGrokBotAgents({ agents: bots.map(bot => bot.name) });
