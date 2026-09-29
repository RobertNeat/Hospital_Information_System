import { describe, expect, it } from 'vitest';
import { FormControl } from '@angular/forms';
import { postalCodeValidator } from './postal-code.validator';

describe('postalCodeValidator', () => {
  it('passes for empty value', () => {
    expect(postalCodeValidator()(new FormControl(''))).toBeNull();
  });

  it('passes for a valid NN-NNN code', () => {
    expect(postalCodeValidator()(new FormControl('00-001'))).toBeNull();
  });

  it('fails for a malformed code', () => {
    expect(postalCodeValidator()(new FormControl('00001'))).toEqual({ postalCode: true });
    expect(postalCodeValidator()(new FormControl('AB-CDE'))).toEqual({ postalCode: true });
  });
});
