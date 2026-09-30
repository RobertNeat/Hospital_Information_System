import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

const EMPLOYEE_ID_RE = /^[A-Za-z0-9-]{4,20}$/;
const PWZ_RE = /^\d{7}$/;

/** Internal employee identifier (login): 4–20 letters, digits or hyphens. Empty values pass. */
export function employeeIdValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string | null;
    return !value || EMPLOYEE_ID_RE.test(value) ? null : { employeeId: true };
  };
}

/** PWZ (Prawo Wykonywania Zawodu) number: exactly 7 digits. Empty values pass. */
export function pwzValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value as string | null;
    return !value || PWZ_RE.test(value) ? null : { pwz: true };
  };
}
