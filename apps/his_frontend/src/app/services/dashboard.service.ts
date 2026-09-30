import { Injectable, inject } from '@angular/core';
import { forkJoin, map } from 'rxjs';
import type { Observable } from 'rxjs';
import type { DashboardStats, OrderStatus } from '../models';
import { ImagingOrderService } from './imaging-order.service';
import { LabOrderService } from './lab-order.service';
import { LabResultService } from './lab-result.service';
import { PatientService } from './patient.service';
import { StaffService } from './staff.service';
import { TeamMessageService } from './team-message.service';
import { VitalsService } from './vitals.service';

/** Order statuses treated as in-flight (not yet completed or cancelled). */
const IN_FLIGHT_STATUSES: readonly OrderStatus[] = [
  'ordered',
  'scheduled',
  'specimen_collected',
  'in_progress',
];

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly patientService = inject(PatientService);
  private readonly teamMessageService = inject(TeamMessageService);
  private readonly labResultService = inject(LabResultService);
  private readonly labOrderService = inject(LabOrderService);
  private readonly imagingOrderService = inject(ImagingOrderService);
  private readonly vitalsService = inject(VitalsService);
  private readonly staffService = inject(StaffService);

  getStats(): Observable<DashboardStats> {
    const currentUserId = this.staffService.currentUser().id;
    return forkJoin({
      admitted: this.patientService.getPatients({ status: 'admitted' }),
      newResults: this.labResultService.getRecent('all'),
      criticalAlerts: this.teamMessageService.getAlerts({ acknowledged: false }),
      openTasks: this.teamMessageService.getTasks({ assignedToId: currentUserId, status: 'open' }),
      labOrders: this.labOrderService.getOrders(),
      imagingOrders: this.imagingOrderService.getOrders(),
      vitalsOverview: this.vitalsService.getWardOverview(),
    }).pipe(
      map(
        ({
          admitted,
          newResults,
          criticalAlerts,
          openTasks,
          labOrders,
          imagingOrders,
          vitalsOverview,
        }) => ({
          admittedPatients: admitted.length,
          newResults: newResults.length,
          criticalAlerts: criticalAlerts.filter((a) => a.severity === 'critical').length,
          openTasks: openTasks.length,
          pendingOrders: [...labOrders, ...imagingOrders].filter((o) =>
            IN_FLIGHT_STATUSES.includes(o.status),
          ).length,
          vitalsAnomalies: vitalsOverview.filter((r) => r.anomalies.length > 0).length,
        }),
      ),
    );
  }
}
