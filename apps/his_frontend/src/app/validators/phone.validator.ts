import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Accepts '+48' followed by 9 digits, or a bare 9-digit number, with optional spaces. */
const PHONE_RE = /^(\+48)?\s?\d{3}\s?\d{3}\s?\d{3}$/;

/** Polish phone number: +48 and 9 digits (or 9 digits alone). Empty values pass. */
export function phoneValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string | null;
    if (!value) return null;
    return PHONE_RE.test(value.trim()) ? null : { phone: true };
  };
}
