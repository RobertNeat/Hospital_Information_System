import type { ID } from './common.model';

/** Hospital staff roles. Messaging UI (recipients, task assignees) still uses only doctor and nurse. */
export type StaffRole =
  'doctor' | 'nurse' | 'lab_technician' | 'radiologist' | 'pharmacist' | 'registrar' | 'admin';

export type StaffAccountStatus = 'pending' | 'active' | 'locked';

export interface StaffMember {
  id: ID;
  /** e.g. 'lek.', 'dr n. med.', 'piel.', 'mgr piel.'. */
  title: string;
  firstName: string;
  lastName: string;
  role: StaffRole;
  specialization?: string;
  wardId: ID;
  phone?: string;
  /** PWZ (Prawo Wykonywania Zawodu) number: 7 digits. */
  pwz?: string;
  /** Internal employee identifier, used as the login. */
  employeeId?: string;
  email?: string;
  accountStatus?: StaffAccountStatus;
  /** @projection Presence computed by the backend. */
  online: boolean;
}

export interface Ward {
  id: ID;
  name: string;
  shortName: string;
  floor: string;
  /** Liczba łóżek. */
  beds: number;
}
