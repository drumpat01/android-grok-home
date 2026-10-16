import { defineAgent } from '@cursor/bdk';

// The tablet bridge calls tools directly, without an intermediary model turn.
export default defineAgent({ tools: [] });
