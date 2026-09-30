import type { StaffRegistrationRequest } from '../../models/api';

/** Raw register form value (includes UI-only fields). */
export type RegisterFormValue = StaffRegistrationRequest & {
  passwordConfirm: string;
  acceptTerms: boolean;
};

/** Maps the register form to the API request, dropping UI-only fields and empty optionals. */
export function toRequest(value: RegisterFormValue): StaffRegistrationRequest {
  const { passwordConfirm: _confirm, acceptTerms: _accept, phone, email, ...rest } = value;
  void _confirm;
  void _accept;
  return {
    ...rest,
    ...(phone?.trim() ? { phone: phone.trim() } : {}),
    ...(email?.trim() ? { email: email.trim() } : {}),
  };
}
