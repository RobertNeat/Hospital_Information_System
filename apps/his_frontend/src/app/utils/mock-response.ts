import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';

/**
 * Builds a mock HTTP-like response.
 *
 * ADDENDUM fix: this is a plain function, not a service, and it must NOT call
 * `inject()` itself -- `inject()` only works inside an active injection context,
 * and this function is invoked at arbitrary runtime (e.g. inside a `.subscribe()`
 * callback) where there is none. Every service instead injects `MOCK_LATENCY_MS`
 * as a class field and passes it in explicitly:
 *
 *   private readonly latency = inject(MOCK_LATENCY_MS);
 *   getX(): Observable<X> { return mockResponse(this.items, this.latency); }
 *
 * When `latencyMs === 0` (as tests set it), this returns `of(...)` synchronously
 * with no `delay()` operator at all -- zoneless `fixture.whenStable()` does not
 * wait out rxjs timers, so a `delay(0)` would leave a test fixture stuck loading.
 */
export function mockResponse<T>(data: T, latencyMs: number): Observable<T> {
  const cloned = structuredClone(data);
  return latencyMs > 0 ? of(cloned).pipe(delay(latencyMs)) : of(cloned);
}

/** Mock error response, e.g. for "patient not found". */
export function mockError<T = never>(message: string, latencyMs: number): Observable<T> {
  return new Observable<T>((subscriber) => {
    const emit = () => subscriber.error(new Error(message));
    if (latencyMs > 0) {
      setTimeout(emit, latencyMs);
    } else {
      emit();
    }
  });
}

/** Generates a sequential-looking mock id, e.g. `nextId('pat')` -> `'pat-016'`. */
export function nextId(prefix: string, sequence: number): string {
  return `${prefix}-${String(sequence).padStart(3, '0')}`;
}
