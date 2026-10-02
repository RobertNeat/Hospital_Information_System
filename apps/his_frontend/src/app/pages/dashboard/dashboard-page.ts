import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import type { Observable } from 'rxjs';
import { DataTable } from '../../components/data-table/data-table';
import {
  IconActionGroup,
  type IconAction,
} from '../../components/icon-action-group/icon-action-group';
import { PatientSearch } from '../../components/patient-search/patient-search';
import { SectionHeader } from '../../components/section-header/section-header';
import { StatCard } from '../../components/stat-card/stat-card';
import { EmptyState } from '../../components/empty-state/empty-state';
import { FullNamePipe } from '../../pipes/full-name.pipe';
import { AgePipe } from '../../pipes/age.pipe';
import { AuthService } from '../../services/auth.service';
import { DashboardService } from '../../services/dashboard.service';
import { LabResultService } from '../../services/lab-result.service';
import { PatientContextService } from '../../services/patient-context.service';
import { PatientService } from '../../services/patient.service';
import { StaffService } from '../../services/staff.service';
import { TeamMessageService } from '../../services/team-message.service';
import { PERMISSIONS } from '../../constants/permissions';
import { alertRoute, type AlertRoute } from '../../utils/alert-route';
import type {
  ClinicalAlert,
  DashboardStats,
  LabResult,
  PatientSummary,
  ResultWithPatient,
  TeamTask,
} from '../../models';

interface QuickActionDef extends IconAction {
  /** Omitted = always visible (e.g. patient registration, messages). */
  requiresAnyOf?: string[];
}

const QUICK_ACTIONS: QuickActionDef[] = [
  { id: 'register-patient', icon: 'pi pi-user-plus', label: 'Rejestracja pacjenta' },
  {
    id: 'lab-order',
    icon: 'pi pi-eye-dropper',
    label: 'Zlecenie laboratoryjne',
    requiresAnyOf: [PERMISSIONS.LAB_ORDER_CREATE],
  },
  {
    id: 'imaging-order',
    icon: 'pi pi-image',
    label: 'Zlecenie obrazowe',
    requiresAnyOf: [PERMISSIONS.IMAGING_ORDER_CREATE],
  },
  {
    id: 'prescription',
    icon: 'pi pi-file-edit',
    label: 'Nowa recepta',
    requiresAnyOf: [PERMISSIONS.PRESCRIPTION_CREATE],
  },
  {
    id: 'vitals',
    icon: 'pi pi-heart',
    label: 'Parametry życiowe',
    requiresAnyOf: [PERMISSIONS.VITALS_READ],
  },
  { id: 'messages', icon: 'pi pi-comments', label: 'Wiadomości' },
];

@Component({
  selector: 'app-dashboard-page',
  imports: [
    RouterLink,
    DatePipe,
    DataTable,
    IconActionGroup,
    PatientSearch,
    SectionHeader,
    StatCard,
    EmptyState,
    FullNamePipe,
    AgePipe,
  ],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'dashboard-page' },
})
export class DashboardPage {
  private readonly router = inject(Router);
  private readonly dashboardService = inject(DashboardService);
  private readonly labResultService = inject(LabResultService);
  private readonly teamMessageService = inject(TeamMessageService);
  private readonly patientService = inject(PatientService);
  private readonly auth = inject(AuthService);
  protected readonly staffService = inject(StaffService);
  protected readonly ctx = inject(PatientContextService);

  protected readonly today = new Date();
  /** Hides quick actions the current role has no permission for. */
  protected readonly quickActions = computed<IconAction[]>(() =>
    QUICK_ACTIONS.filter(
      (a) => !a.requiresAnyOf || a.requiresAnyOf.some((p) => this.auth.hasPermission(p)),
    ),
  );

  protected readonly stats = signal<DashboardStats | null>(null);
  protected readonly criticalAlerts = signal<ClinicalAlert[]>([]);
  protected readonly myTasks = signal<TeamTask[]>([]);
  protected readonly abnormalResults = signal<ResultWithPatient<LabResult>[]>([]);
  protected readonly fallbackAdmitted = signal<PatientSummary[]>([]);
  protected readonly loading = signal(true);
  /** Names of patients missing from inbox rows (`patient` is optional), resolved by id. */
  private readonly patientNames = signal<Record<string, string>>({});

  protected readonly taskColumns = [
    { field: 'title', header: 'Zadanie' },
    { field: 'priority', header: 'Priorytet', type: 'tag' as const, tagKind: 'priority' as const },
    { field: 'status', header: 'Status', type: 'tag' as const, tagKind: 'taskStatus' as const },
    { field: 'dueAt', header: 'Termin', type: 'datetime' as const },
  ];

  constructor() {
    const currentUserId = this.staffService.currentUser().id;
    // Each widget's source may be forbidden for the current role (e.g. pharmacist lacks
    // `task:read`/`alert:read`); fall back to an empty result instead of failing every
    // widget on one 403 (naprawiony blad: dashboard szedl na pusto dla ról bez pelnych uprawnien).
    const orEmpty = <T>(source: Observable<T>, fallback: T): Observable<T> =>
      source.pipe(catchError(() => of(fallback)));
    const permittedOrEmpty = <T>(
      permission: string,
      source: () => Observable<T>,
      fallback: T,
    ): Observable<T> =>
      this.auth.hasPermission(permission) ? orEmpty(source(), fallback) : of(fallback);

    forkJoin({
      stats: orEmpty(this.dashboardService.getStats(), null as DashboardStats | null),
      alerts: permittedOrEmpty(
        PERMISSIONS.ALERT_READ,
        () => this.teamMessageService.getAlerts({ acknowledged: false }),
        [] as ClinicalAlert[],
      ),
      tasks: permittedOrEmpty(
        PERMISSIONS.TASK_READ,
        () => this.teamMessageService.getTasks({ assignedToId: currentUserId, status: 'open' }),
        [] as TeamTask[],
      ),
      abnormalResults: permittedOrEmpty(
        PERMISSIONS.LAB_RESULT_READ,
        () => this.labResultService.getRecent('abnormal'),
        [] as ResultWithPatient<LabResult>[],
      ),
      admitted: orEmpty(
        this.patientService.getPatients({ status: 'admitted' }),
        [] as PatientSummary[],
      ),
    })
      .pipe(takeUntilDestroyed())
      .subscribe(({ stats, alerts, tasks, abnormalResults, admitted }) => {
        this.stats.set(stats);
        this.criticalAlerts.set(alerts.filter((a) => a.severity === 'critical'));
        this.myTasks.set(tasks);
        this.abnormalResults.set(abnormalResults);
        this.fallbackAdmitted.set(admitted);
        this.loading.set(false);
        this.resolveMissingPatients(abnormalResults);
      });
  }

  /** Route of an alert from its target; a patient-only alert opens the patient overview. */
  protected alertLink(alert: ClinicalAlert): AlertRoute | null {
    return (
      alertRoute(alert) ??
      (alert.patientId ? { commands: ['/patients', alert.patientId, 'overview'] } : null)
    );
  }

  protected resultPatientName(result: ResultWithPatient<LabResult>): string {
    if (result.patient) return `${result.patient.lastName} ${result.patient.firstName}`;
    return this.patientNames()[result.patientId] ?? '';
  }

  private resolveMissingPatients(results: ResultWithPatient<LabResult>[]): void {
    const missing = new Set(results.filter((r) => !r.patient).map((r) => r.patientId));
    for (const id of missing) {
      this.patientService.getPatientById(id).subscribe({
        next: (p) =>
          this.patientNames.update((m) => ({ ...m, [id]: `${p.lastName} ${p.firstName}` })),
        error: () => undefined,
      });
    }
  }

  protected goToPatient(patient: PatientSummary): void {
    this.router.navigateByUrl(`/patients/${patient.id}/overview`);
  }

  protected onQuickAction(id: string): void {
    switch (id) {
      case 'register-patient':
        this.router.navigateByUrl('/patients/register');
        break;
      case 'lab-order':
        this.router.navigateByUrl('/orders/lab/new');
        break;
      case 'imaging-order':
        this.router.navigateByUrl('/orders/imaging/new');
        break;
      case 'prescription':
        this.router.navigateByUrl('/prescriptions/new');
        break;
      case 'vitals':
        this.router.navigateByUrl('/vitals');
        break;
      case 'messages':
        this.router.navigateByUrl('/messages');
        break;
    }
  }
}
