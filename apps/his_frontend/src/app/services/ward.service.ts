import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import { MOCK_LATENCY_MS } from '../config/mock-api.config';
import { WARDS } from '../mock-data/wards.mock';
import type { Ward } from '../models';
import { mockResponse } from '../utils/mock-response';

@Injectable({ providedIn: 'root' })
export class WardService {
  private readonly latency = inject(MOCK_LATENCY_MS);
  private readonly wards: Ward[] = structuredClone(WARDS);

  getWards(): Observable<Ward[]> {
    return mockResponse(this.wards, this.latency);
  }

  /** Synchronous lookup from the in-memory cache, for pipes/templates. */
  nameOf(id: string): string {
    return this.wards.find((w) => w.id === id)?.name ?? id;
  }
}
