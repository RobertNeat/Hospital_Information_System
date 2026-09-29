import { defineConfig } from 'vitest/config';

// Raises the per-test timeout and caps worker concurrency: the default 5s timeout was too tight for ~90 spec files running in parallel under full-suite resource contention.
export default defineConfig({
  test: {
    testTimeout: 15000,
    hookTimeout: 15000,
    pool: 'threads',
    maxWorkers: 4,
  },
});
