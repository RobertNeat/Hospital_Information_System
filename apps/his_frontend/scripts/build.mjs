#!/usr/bin/env node
// Wraps `ng build` to pass HIS_PERSIST_SESSION (default: true) as a compile-time
// `define` for src/app/config/session.config.ts, overriding the angular.json default.
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';

const persistSession = (process.env.HIS_PERSIST_SESSION ?? 'true') !== 'false';

// Resolve the Angular CLI's JS entrypoint directly and run it with `process.execPath`
// instead of `npx ng` with `shell: true`: avoids spawning a shell (command injection
// risk) while still working on Windows, where `npx`/`ng` are `.cmd` shims Node refuses
// to spawn without a shell.
const require = createRequire(import.meta.url);
const ngBin = require.resolve('@angular/cli/bin/ng.js');

const result = spawnSync(
  process.execPath,
  [
    ngBin,
    'build',
    `--define=HIS_PERSIST_SESSION=${JSON.stringify(String(persistSession))}`,
    ...process.argv.slice(2),
  ],
  { stdio: 'inherit' },
);

process.exit(result.status ?? 1);
