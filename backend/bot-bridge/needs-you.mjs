/** Native waiting state for Raise Your Hand. Never inferred from message text. */

export const QUESTION_CAP = 600;
export const QUESTION_SHORT_MAX = 25;
/** A cut leaves room for the tile ellipsis as the 25th character. */
export const QUESTION_SHORT_CUT = 24;
const OUTCOMES = new Set(['complete', 'error', 'cancelled']);

/** Drop cut-form punctuation. A trailing ? stays. Fitting text is not rewritten. */
function stripTrailingPunctuation(value) {
  let end = value.length;
  while (end > 0) {
    const ch = value[end - 1];
    if (ch === '?') break;
    if (!/\p{P}/u.test(ch)) break;
    end -= 1;
  }
  return value.slice(0, end);
}

/**
 * Native title wins, then the instruction. Text that fits in 25 is unchanged.
 * A cut is at most 24 characters on a word boundary. Nothing is added or reordered.
 */
export function questionShort(question, title = '') {
  const nativeTitle = typeof title === 'string' ? title.trim() : '';
  const source = nativeTitle || (typeof question === 'string' ? question.trim() : '');
  if (!source) return {text: '', truncated: false};
  if (source.length <= QUESTION_SHORT_MAX) return {text: source, truncated: false};
  const words = source.split(/\s+/).filter(Boolean);
  if (words[0].length > QUESTION_SHORT_CUT) return {text: words[0].slice(0, QUESTION_SHORT_CUT), truncated: true};
  let short = words[0];
  for (const word of words.slice(1)) {
    const candidate = `${short} ${word}`;
    if (candidate.length > QUESTION_SHORT_CUT) break;
    short = candidate;
  }
  return {text: stripTrailingPunctuation(short), truncated: true};
}

function text(value, max) {
  return typeof value === 'string' ? value.trim().slice(0, max) : '';
}

function positive(value) {
  return typeof value === 'number' && Number.isFinite(value) && value > 0 ? Math.trunc(value) : 0;
}

/**
 * Pending approval from ListGrokBotAgentStatuses, and only when that payload
 * carries boxHandoffRequestId. Transcript prose is ignored.
 */
export function needsYouFromStatus(status, now = Date.now()) {
  if (!status || typeof status !== 'object' || Array.isArray(status)) return [];
  const requestId = text(status.boxHandoffRequestId, 180);
  if (!requestId) return [];
  const awaiting = status.awaiting && typeof status.awaiting === 'object' ? status.awaiting : {};
  const question = text(status.boxHandoffInstruction, QUESTION_CAP);
  const title = text(status.boxHandoffTitle, 80) || text(awaiting.title, 80);
  const short = questionShort(question, title);
  const item = {
    id: `handoff:${requestId}`,
    kind: 'approval',
    question,
    question_short: short.text,
    question_short_truncated: short.truncated,
    context: text(awaiting.reason, 80),
    options: [],
    created: positive(awaiting.sinceMs) || positive(status.updatedAtMs) || positive(now),
    expires: null,
    answerable_here: false,
  };
  if (title) item.title = title;
  return [item];
}

/** Terminal run outcome only when the status object itself reports one. */
export function lastRunFromStatus(status) {
  const raw = status && typeof status === 'object' ? status.lastRun : null;
  if (!raw || typeof raw !== 'object' || !OUTCOMES.has(raw.outcome)) return null;
  if (typeof raw.at !== 'number' || !Number.isFinite(raw.at)) return null;
  return {outcome: raw.outcome, at: Math.trunc(raw.at)};
}

/** Drop items the owner already answered, and items whose own expiry has passed. */
export function dropAnswered(items, entries, now = Date.now()) {
  const times = (entries || []).filter(row => row && row.kind === 'message' && row.role === 'You').map(row => Number(row.time) || 0);
  const latestYou = times.length ? Math.max(...times) : null;
  return (items || []).filter(item => {
    if (item.expires != null && Number(item.expires) <= now) return false;
    if (latestYou != null && Number(item.created) < latestYou) return false;
    return true;
  });
}
