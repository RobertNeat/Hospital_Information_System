import type { ID, ISODateTime } from '../common.model';
import type { StaffMember, StaffRole } from '../staff.model';

export interface LoginRequest {
  employeeId: string;
  password: string;
}

export type CurrentUser = StaffMember & {
  /** @viewerScoped Permissions granted to the authenticated user. */
  permissions?: string[];
};

export interface LoginResponse {
  /** Omitted when the session is cookie-based. */
  accessToken?: string;
  user: CurrentUser;
  expiresAt: ISODateTime;
}

export interface StaffRegistrationRequest {
  firstName: string;
  lastName: string;
  role: StaffRole;
  title: string;
  specialization: string;
  pwz: string;
  wardId: ID;
  phone?: string;
  email?: string;
  employeeId: string;
  password: string;
}

export interface StaffRegistrationResponse {
  staffId: ID;
  accountStatus: 'pending';
}
