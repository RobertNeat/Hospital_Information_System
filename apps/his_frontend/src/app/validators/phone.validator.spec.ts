import { describe, expect, it } from 'vitest';
import { FormControl } from '@angular/forms';
import { phoneValidator } from './phone.validator';

describe('phoneValidator', () => {
  it('passes for empty value', () => {
    expect(phoneValidator()(new FormControl(''))).toBeNull();
  });

  it('passes for +48 and 9 digits', () => {
    expect(phoneValidator()(new FormControl('+48 512 000 001'))).toBeNull();
  });

  it('passes for a bare 9-digit number', () => {
    expect(phoneValidator()(new FormControl('512000001'))).toBeNull();
  });

  it('fails for too few digits', () => {
    expect(phoneValidator()(new FormControl('12345'))).toEqual({ phone: true });
  });
});
