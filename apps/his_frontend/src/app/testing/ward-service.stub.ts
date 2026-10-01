import { signal } from '@angular/core';
import { of } from 'rxjs';
import { WARDS } from '../mock-data/wards.mock';
import { WardService } from '../services/ward.service';

/** Test provider replacing the HTTP-backed `WardService` with the mock ward list. */
export const wardServiceStub = {
  provide: WardService,
  useValue: {
    wards: signal(WARDS),
    getWards: () => of(WARDS),
    load: () => of(WARDS),
    nameOf: (id: string) => WARDS.find((w) => w.id === id)?.name ?? id,
  },
};
