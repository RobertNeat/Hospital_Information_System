import { defineConfig } from 'vitest/config';

// The mocks call `new Date()` at import time (daysAgo/hoursAgo/isoAt), and the generator pins the
// timezone so the derived offsets do not depend on the machine. TZ has to be set before the worker
// process starts, hence the top-level assignment and `pool: 'forks'` (workers inherit the env).
process.env['TZ'] = 'Europe/Warsaw';

// Separate from the regular `ng test` run: only `*.export.ts` files under scripts/export-mocks.
export default defineConfig({
  test: {
    include: ['scripts/export-mocks/**/*.export.ts'],
    environment: 'node',
    pool: 'forks',
    testTimeout: 60000,
    hookTimeout: 60000,
  },
});
