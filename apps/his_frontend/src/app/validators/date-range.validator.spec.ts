import { describe, expect, it } from 'vitest';
import { FormControl, FormGroup } from '@angular/forms';
import { dateRangeValidator } from './date-range.validator';

describe('dateRangeValidator', () => {
  it('passes when from <= to', () => {
    const group = new FormGroup({
      from: new FormControl('2026-01-01'),
      to: new FormControl('2026-01-31'),
    });
    expect(dateRangeValidator('from', 'to')(group)).toBeNull();
  });

  it('fails when from > to', () => {
    const group = new FormGroup({
      from: new FormControl('2026-02-01'),
      to: new FormControl('2026-01-01'),
    });
    expect(dateRangeValidator('from', 'to')(group)).toEqual({ dateRange: true });
  });

  it('passes when either value is missing', () => {
    const group = new FormGroup({
      from: new FormControl(null),
      to: new FormControl('2026-01-01'),
    });
    expect(dateRangeValidator('from', 'to')(group)).toBeNull();
  });
});
