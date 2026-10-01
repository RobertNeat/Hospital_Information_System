import type { ApiError } from '../utils/api-error';

export const LOGIN_INVALID_CREDENTIALS = 'Nieprawidłowy login lub hasło.';
export const REGISTER_PENDING_MESSAGE =
  'Konto zostało utworzone i oczekuje na aktywację przez administratora.';

const NETWORK_ERROR = 'Brak połączenia z serwerem. Spróbuj ponownie za chwilę.';
const GENERIC_ERROR = 'Wystąpił nieoczekiwany błąd. Spróbuj ponownie.';

/** Polish message for a failed `POST /auth/login`. */
export function loginErrorMessage(error: ApiError): string {
  switch (error.status) {
    case 0:
      return NETWORK_ERROR;
    case 401:
      return LOGIN_INVALID_CREDENTIALS;
    case 403:
      return error.problem.detail ?? 'Konto jest nieaktywne lub zablokowane.';
    case 422:
      return error.fieldErrors.length > 0
        ? error.fieldErrors.map((e) => e.message).join(' ')
        : 'Podaj identyfikator pracownika i hasło.';
    default:
      return GENERIC_ERROR;
  }
}

/** Polish message for a failed `POST /auth/register` (field errors are shown on the fields). */
export function registerErrorMessage(error: ApiError): string {
  switch (error.status) {
    case 0:
      return NETWORK_ERROR;
    case 409:
      return (
        error.problem.detail ??
        'Konto z takim identyfikatorem, numerem PWZ lub adresem e-mail już istnieje.'
      );
    case 422:
      return 'Popraw zaznaczone pola formularza.';
    case 403:
      return error.problem.detail ?? 'Rejestracja jest niedostępna.';
    default:
      return GENERIC_ERROR;
  }
}
