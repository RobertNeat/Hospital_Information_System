import type { ID } from './common.model';

/** The only defined messaging actors. */
export type StaffRole = 'doctor' | 'nurse';

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
  online: boolean;
}

export interface Ward {
  id: ID;
  name: string;
  shortName: string;
  floor: string;
  beds: number;
}
