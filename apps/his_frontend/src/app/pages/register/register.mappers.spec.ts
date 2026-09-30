import { describe, expect, it } from 'vitest';
import { toRequest } from './register.mappers';
import type { RegisterFormValue } from './register.mappers';

const base: RegisterFormValue = {
  firstName: 'Jan',
  lastName: 'Kowalski',
  role: 'doctor',
  title: 'lek.',
  specialization: 'Kardiologia',
  pwz: '1234567',
  wardId: 'ward-kar',
  phone: '',
  employeeId: 'EMP-1234',
  password: 'secret123',
  passwordConfirm: 'secret123',
  acceptTerms: true,
};

describe('toRequest', () => {
  it('drops UI-only fields and empty optionals', () => {
    const req = toRequest(base);
    expect(req).not.toHaveProperty('passwordConfirm');
    expect(req).not.toHaveProperty('acceptTerms');
    expect(req).not.toHaveProperty('phone');
    expect(req).not.toHaveProperty('email');
    expect(req.employeeId).toBe('EMP-1234');
  });

  it('keeps provided phone', () => {
    expect(toRequest({ ...base, phone: ' +48 600 000 000 ' }).phone).toBe('+48 600 000 000');
  });
});
