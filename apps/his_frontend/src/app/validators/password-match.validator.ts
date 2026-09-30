import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Group-level validator: `passwordMismatch` when the two named controls differ. */
export function passwordMatchValidator(passwordKey: string, confirmKey: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get(passwordKey)?.value as string | null;
    const confirm = group.get(confirmKey)?.value as string | null;
    return password && confirm && password !== confirm ? { passwordMismatch: true } : null;
  };
}
