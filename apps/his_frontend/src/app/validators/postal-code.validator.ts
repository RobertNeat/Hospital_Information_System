import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

const POSTAL_CODE_RE = /^\d{2}-\d{3}$/;

/** Polish postal code format NN-NNN, e.g. '00-001'. Empty values pass. */
export function postalCodeValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string | null;
    if (!value) return null;
    return POSTAL_CODE_RE.test(value) ? null : { postalCode: true };
  };
}
