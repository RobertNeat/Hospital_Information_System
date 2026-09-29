import { describe, expect, it } from 'vitest';
import { LabelPipe } from './label.pipe';

describe('LabelPipe', () => {
  const pipe = new LabelPipe();

  it('resolves a known value via the given map key', () => {
    expect(pipe.transform('urgent', 'urgency')).toBe('Pilne');
    expect(pipe.transform('stat', 'urgency')).toBe('Natychmiastowe (CITO)');
  });

  it('resolves admissionStatus labels', () => {
    expect(pipe.transform('admitted', 'admissionStatus')).toBe('Przyjęty');
  });

  it('falls back to the raw value for an unknown value', () => {
    expect(pipe.transform('unknown-value', 'urgency')).toBe('unknown-value');
  });

  it('returns an empty string for null/undefined', () => {
    expect(pipe.transform(null, 'urgency')).toBe('');
    expect(pipe.transform(undefined, 'urgency')).toBe('');
  });
});
