import { defineTool } from '@cursor/bdk/tools';
import { z } from 'zod';
import { configuredBots } from '../../config.mjs';
import { NativeActivity } from '../../native-activity.mjs';
import { resolveApiKeySync } from '../../node_modules/@cursor/bdk/dist/internal/cursor/credentials.js';
import { exchangeCursorApiKeyForSession } from '../../node_modules/@cursor/bdk/dist/internal/cursor/session-token.js';

const reader=new NativeActivity({credential:()=>resolveApiKeySync({})?.apiKey,
  exchange:key=>exchangeCursorApiKeyForSession({exchangeUrl:'https://api2.cursor.sh/auth/exchange_user_api_key',apiKey:key})});
export default defineTool({description:'Read native messages, explicit bot contacts and working status. Never sends or marks read.',effect:'read',
  inputSchema:z.object({agent:z.string().min(1).max(255)}),
  async execute(input){return reader.snapshot(input.agent,configuredBots().map(bot=>bot.name));}});
