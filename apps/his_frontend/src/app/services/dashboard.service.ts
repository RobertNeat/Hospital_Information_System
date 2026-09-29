import { Injectable, inject } from '@angular/core';
import { forkJoin, map } from 'rxjs';
import type { Observable } from 'rxjs';
import type { DashboardStats } from '../models';
import { LabOrderService } from './lab-order.service';
import { LabResultService } from './lab-result.service';
import { PatientService } from './patient.service';
import { StaffService } from './staff.service';
import { TeamMessageService } from './team-message.service';
import { VitalsService } from './vitals.service';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly patientService = inject(PatientService);
  private readonly teamMessageService = inject(TeamMessageService);
  private readonly labResultService = inject(LabResultService);
  private readonly labOrderService = inject(LabOrderService);
  private readonly vitalsService = inject(VitalsService);
  private readonly staffService = inject(StaffService);

  getStats(): Observable<DashboardStats> {
    const currentUserId = this.staffService.currentUser().id;
    return forkJoin({
      admitted: this.patientService.getPatients({ status: 'admitted' }),
      newResults: this.labResultService.getRecent('all'),
      criticalAlerts: this.teamMessageService.getAlerts({ acknowledged: false }),
      openTasks: this.teamMessageService.getTasks({ assignedToId: currentUserId, status: 'open' }),
      pendingOrders: this.labOrderService.getOrders({ status: 'ordered' }),
      vitalsOverview: this.vitalsService.getWardOverview(),
    }).pipe(
      map(({ admitted, newResults, criticalAlerts, openTasks, pendingOrders, vitalsOverview }) => ({
        admittedPatients: admitted.length,
        newResults: newResults.length,
        criticalAlerts: criticalAlerts.filter((a) => a.severity === 'critical').length,
        openTasks: openTasks.length,
        pendingOrders: pendingOrders.length,
        vitalsAnomalies: vitalsOverview.filter((r) => r.anomalies.length > 0).length,
      })),
    );
  }
}
