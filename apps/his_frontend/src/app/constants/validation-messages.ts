/**
 * Polish error messages keyed by Angular validator error key.
 * Consumed by `app-form-field` (Phase 0b) to render the first active error
 * under a control, e.g. `VALIDATION_MESSAGES['required'](control.errors!['required'])`.
 */
export const VALIDATION_MESSAGES: Record<string, (error?: unknown) => string> = {
  required: () => 'To pole jest wymagane.',
  email: () => 'Nieprawidłowy adres e-mail.',
  minlength: (error) => {
    const e = error as { requiredLength: number; actualLength: number } | undefined;
    return e ? `Minimalna długość to ${e.requiredLength} znaków.` : 'Za krótka wartość.';
  },
  maxlength: (error) => {
    const e = error as { requiredLength: number; actualLength: number } | undefined;
    return e ? `Maksymalna długość to ${e.requiredLength} znaków.` : 'Za długa wartość.';
  },
  min: (error) => {
    const e = error as { min: number } | undefined;
    return e ? `Wartość musi być większa lub równa ${e.min}.` : 'Za mała wartość.';
  },
  max: (error) => {
    const e = error as { max: number } | undefined;
    return e ? `Wartość musi być mniejsza lub równa ${e.max}.` : 'Za duża wartość.';
  },
  pattern: () => 'Nieprawidłowy format.',
  pesel: () => 'Nieprawidłowy numer PESEL.',
  postalCode: () => 'Nieprawidłowy kod pocztowy (format NN-NNN).',
  phone: () => 'Nieprawidłowy numer telefonu.',
  pwz: () => 'Numer PWZ składa się z 7 cyfr.',
  employeeId: () => 'Dozwolone: litery, cyfry i myślnik (4–20 znaków).',
  /** Error returned by the API for this field (`{ server: message }`). */
  server: (error) => (typeof error === 'string' ? error : DEFAULT_VALIDATION_MESSAGE),
  passwordMismatch: () => 'Hasła muszą być takie same.',
  dateRange: () => 'Nieprawidłowy zakres dat.',
};

/** Fallback message when an error key has no specific mapping above. */
export const DEFAULT_VALIDATION_MESSAGE = 'Wartość jest nieprawidłowa.';
