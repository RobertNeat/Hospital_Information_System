import { describe, expect, it, vi, afterEach } from 'vitest';
import { AgePipe } from './age.pipe';

describe('AgePipe', () => {
  const pipe = new AgePipe();

  afterEach(() => {
    vi.useRealTimers();
  });

  it('formats age as "N l."', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-03-14T00:00:00'));
    expect(pipe.transform('1968-03-14')).toBe('58 l.');
  });

  it('returns an empty string for null/undefined', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
