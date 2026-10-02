import type { StaffRole } from '../models';

export interface DemoAccount {
  employeeId: string;
  password: string;
  name: string;
  role: StaffRole;
}

const MOCK_PASSWORD = 'HisDemo2026!';

/** Demo-only list shown on the login page; mirrors db/changelog/demo and mock seed data. */
export const DEMO_ACCOUNTS: DemoAccount[] = [
  { employeeId: 'admin', password: 'admin', name: 'Demo Admin', role: 'admin' },
  { employeeId: 'user', password: 'user', name: 'Demo Użytkownik', role: 'doctor' },
  { employeeId: 'doctor', password: 'doctor', name: 'Demo Lekarz', role: 'doctor' },
  { employeeId: 'nurse', password: 'nurse', name: 'Demo Pielęgniarka', role: 'nurse' },
  { employeeId: 'lab-tech', password: 'lab-tech', name: 'Demo Laborant', role: 'lab_technician' },
  {
    employeeId: 'radiologist',
    password: 'radiologist',
    name: 'Demo Radiolog',
    role: 'radiologist',
  },
  { employeeId: 'pharmacist', password: 'pharmacist', name: 'Demo Farmaceuta', role: 'pharmacist' },
  { employeeId: 'registrar', password: 'registrar', name: 'Demo Rejestrator', role: 'registrar' },
  { employeeId: 'EMP-0001', password: MOCK_PASSWORD, name: 'Anna Nowak', role: 'doctor' },
  { employeeId: 'EMP-0002', password: MOCK_PASSWORD, name: 'Piotr Wiśniewski', role: 'doctor' },
  { employeeId: 'EMP-0003', password: MOCK_PASSWORD, name: 'Tomasz Kamiński', role: 'doctor' },
  { employeeId: 'EMP-0004', password: MOCK_PASSWORD, name: 'Marta Lewandowska', role: 'doctor' },
  { employeeId: 'EMP-0005', password: MOCK_PASSWORD, name: 'Paweł Zieliński', role: 'doctor' },
  { employeeId: 'EMP-0006', password: MOCK_PASSWORD, name: 'Katarzyna Zielińska', role: 'nurse' },
  { employeeId: 'EMP-0007', password: MOCK_PASSWORD, name: 'Magdalena Wójcik', role: 'nurse' },
  { employeeId: 'EMP-0008', password: MOCK_PASSWORD, name: 'Ewa Lewandowska', role: 'nurse' },
  { employeeId: 'EMP-0009', password: MOCK_PASSWORD, name: 'Agnieszka Kaczmarek', role: 'nurse' },
  { employeeId: 'EMP-0010', password: MOCK_PASSWORD, name: 'Michał Szymański', role: 'nurse' },
];
