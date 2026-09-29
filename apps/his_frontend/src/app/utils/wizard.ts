import type { AbstractControl } from '@angular/forms';

/**
 * Validates `form`, marks it touched, and advances to `next` via `activate` if valid.
 * Used by every wizard step footer's "Dalej"/"Zatwierdź" handler.
 */
export function tryAdvance(
  form: AbstractControl,
  activate: (value: number) => void,
  next: number,
): boolean {
  form.markAllAsTouched();
  form.updateValueAndValidity();
  if (form.valid) {
    activate(next);
    return true;
  }
  return false;
}
