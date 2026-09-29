import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { StaffNamePipe } from './staff-name.pipe';

describe('StaffNamePipe', () => {
  let pipe: StaffNamePipe;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [{ provide: MOCK_LATENCY_MS, useValue: 0 }] });
    pipe = TestBed.runInInjectionContext(() => new StaffNamePipe());
  });

  it('resolves a staff id to "title firstName lastName"', () => {
    expect(pipe.transform('stf-001')).toBe('lek. Anna Nowak');
  });

  it('returns an empty string for null/undefined', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
  });
});
