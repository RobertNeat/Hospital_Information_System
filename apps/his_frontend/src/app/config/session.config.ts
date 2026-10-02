/**
 * Replaced at build time by the `define` entry in angular.json from the
 * `HIS_PERSIST_SESSION` environment variable (default: `'true'`).
 */
declare const HIS_PERSIST_SESSION: string;

/**
 * Whether the session (JWT + user) survives a page reload (persisted to `localStorage`)
 * or is lost on reload (kept in memory only). Defaults to persisted; set the
 * `HIS_PERSIST_SESSION=false` environment variable at build time to opt out.
 */
export const PERSIST_SESSION = HIS_PERSIST_SESSION !== 'false';
