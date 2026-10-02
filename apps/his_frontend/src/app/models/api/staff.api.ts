import type { ID } from '../common.model';
import type { StaffRole } from '../staff.model';

export interface StaffQuery {
  role?: StaffRole;
  wardId?: ID;
}

/**
 * `POST /staff/{id}/activate` and `/lock` take no request body (the actor comes from the
 * session) and return the updated `StaffMember`; see `StaffService.activate`/`lock`.
 */
export type StaffAccountAction = 'activate' | 'lock';
