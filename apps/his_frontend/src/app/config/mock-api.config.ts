import { InjectionToken } from '@angular/core';

/**
 * Simulated network latency (ms) applied to every mock service response.
 *
 * Provided at the root with a default of 300ms so any service can `inject()` it
 * without NG0201 even before app.config.ts (Phase 0b) explicitly overrides it.
 * Unit tests override it to 0 so `TestBed` doesn't need fake timers.
 */
export const MOCK_LATENCY_MS = new InjectionToken<number>('MOCK_LATENCY_MS', {
  providedIn: 'root',
  factory: () => 300,
});
