import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Group validator ensuring `control.get(fromKey) <= control.get(toKey)`.
 * Sets `{ dateRange: true }` on the group when both values are present and out of order.
 */
export function dateRangeValidator(fromKey: string, toKey: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const fromValue = group.get(fromKey)?.value as Date | string | null;
    const toValue = group.get(toKey)?.value as Date | string | null;
    if (!fromValue || !toValue) return null;

    const from = new Date(fromValue);
    const to = new Date(toValue);
    return from.getTime() <= to.getTime() ? null : { dateRange: true };
  };
}
