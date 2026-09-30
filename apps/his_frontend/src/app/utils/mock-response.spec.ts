import { describe, expect, it } from 'vitest';
import { firstValueFrom } from 'rxjs';
import { ApiError, mockError, mockResponse, nextId } from './mock-response';

describe('mockResponse', () => {
  it('emits synchronously (no delay) when latencyMs is 0', async () => {
    const obs = mockResponse({ a: 1 }, 0);
    let emitted: unknown;
    obs.subscribe((v) => (emitted = v));
    expect(emitted).toEqual({ a: 1 });
  });

  it('deep-clones the data so mutating the result does not affect the source', () => {
    const source = { items: [1, 2, 3] };
    let result: typeof source | undefined;
    mockResponse(source, 0).subscribe((v) => (result = v));
    result!.items.push(4);
    expect(source.items).toEqual([1, 2, 3]);
  });

  it('delays emission when latencyMs > 0', async () => {
    const start = Date.now();
    const value = await firstValueFrom(mockResponse('x', 20));
    expect(value).toBe('x');
    expect(Date.now() - start).toBeGreaterThanOrEqual(15);
  });
});

describe('mockError', () => {
  it('errors synchronously when latencyMs is 0', async () => {
    await expect(firstValueFrom(mockError('boom', 0))).rejects.toThrow('boom');
  });

  it('errors with ApiError carrying a default 404 ProblemDetail', async () => {
    const error = await firstValueFrom(mockError('boom', 0)).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    const { problem, message } = error as ApiError;
    expect(message).toBe('boom');
    expect(problem.status).toBe(404);
    expect(problem.title).toBe('Not Found');
    expect(problem.detail).toBe('boom');
    expect(problem.code).toBe('NOT_FOUND');
  });

  it('lets the problem argument override defaults', async () => {
    const error = await firstValueFrom(
      mockError('stale', 0, { status: 409, title: 'Conflict', code: 'CONFLICT' }),
    ).catch((e: unknown) => e);
    const { problem, message } = error as ApiError;
    expect(message).toBe('stale');
    expect(problem.status).toBe(409);
    expect(problem.code).toBe('CONFLICT');
    expect(problem.detail).toBe('stale');
  });
});

describe('nextId', () => {
  it('formats a zero-padded sequential id', () => {
    expect(nextId('pat', 16)).toBe('pat-016');
    expect(nextId('stf', 1)).toBe('stf-001');
  });
});
