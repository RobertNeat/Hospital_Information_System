import { signal } from '@angular/core';
import { of } from 'rxjs';
import { STAFF } from '../mock-data/staff.mock';
import { StaffService } from '../services/staff.service';

/** Mock user "lek. Anna Nowak" (stf-001) used as the signed-in user in unit tests. */
export const TEST_USER = STAFF.find((s) => s.id === 'stf-001') ?? STAFF[0];

/** Test provider replacing the HTTP-backed `StaffService` with the mock staff list. */
export const staffServiceStub = {
  provide: StaffService,
  useValue: {
    currentUser: signal(TEST_USER),
    staff: signal(STAFF),
    load: () => of(STAFF),
    getStaff: (role?: string, wardId?: string) =>
      of(STAFF.filter((s) => (!role || s.role === role) && (!wardId || s.wardId === wardId))),
    getById: (id: string) => of(STAFF.find((s) => s.id === id) ?? STAFF[0]),
    nameOf: (id: string) => {
      const s = STAFF.find((m) => m.id === id);
      return s ? `${s.title} ${s.firstName} ${s.lastName}` : id;
    },
    activate: (id: string) =>
      of({ ...(STAFF.find((s) => s.id === id) ?? STAFF[0]), accountStatus: 'active' }),
    lock: (id: string) =>
      of({ ...(STAFF.find((s) => s.id === id) ?? STAFF[0]), accountStatus: 'locked' }),
  },
};
