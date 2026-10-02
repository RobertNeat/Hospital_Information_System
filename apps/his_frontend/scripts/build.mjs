#!/usr/bin/env node
// Wraps `ng build` to pass HIS_PERSIST_SESSION (default: true) as a compile-time
// `define` for src/app/config/session.config.ts, overriding the angular.json default.
import { spawnSync } from 'node:child_process';

const persistSession = (process.env.HIS_PERSIST_SESSION ?? 'true') !== 'false';

const result = spawnSync(
  'npx',
  [
    'ng',
    'build',
    `--define=HIS_PERSIST_SESSION=${JSON.stringify(String(persistSession))}`,
    ...process.argv.slice(2),
  ],
  { stdio: 'inherit', shell: true },
);

process.exit(result.status ?? 1);
