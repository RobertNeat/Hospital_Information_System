import type { ID } from '../common.model';
import type { StaffRole } from '../staff.model';

export interface StaffQuery {
  role?: StaffRole;
  wardId?: ID;
}
