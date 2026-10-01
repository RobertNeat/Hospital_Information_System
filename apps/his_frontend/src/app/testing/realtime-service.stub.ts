import { signal } from '@angular/core';
import { RealtimeService } from '../services/realtime.service';
import type { RealtimeStatus } from '../services/realtime.service';

/** `RealtimeService` double that never opens a WebSocket. */
export const realtimeServiceStub = {
  provide: RealtimeService,
  useFactory: () => ({ status: signal<RealtimeStatus>('connected') }),
};
