import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PERMISSIONS } from '../../constants/permissions';
import { AuthService } from '../../services/auth.service';

interface ChartNavItem {
  label: string;
  segment: string;
  /** Mirrors the segment route's `permissionGuard` (see app.routes.ts) so a role without
   *  access never sees a tab that would bounce it back to the dashboard on click. */
  requiresAnyOf?: string[];
}

const ITEMS: ChartNavItem[] = [
  { label: 'Dane pacjenta', segment: 'overview' },
  {
    label: 'Historia choroby',
    segment: 'history',
    requiresAnyOf: [PERMISSIONS.EHR_READ, PERMISSIONS.EHR_READ_LIMITED],
  },
  {
    label: 'Wyniki badań',
    segment: 'results',
    requiresAnyOf: [PERMISSIONS.LAB_RESULT_READ, PERMISSIONS.IMAGING_RESULT_READ],
  },
  { label: 'Parametry życiowe', segment: 'vitals', requiresAnyOf: [PERMISSIONS.VITALS_READ] },
  {
    label: 'Zlecenia',
    segment: 'orders',
    requiresAnyOf: [PERMISSIONS.LAB_ORDER_READ, PERMISSIONS.IMAGING_ORDER_READ],
  },
  {
    label: 'Leki i recepty',
    segment: 'prescriptions',
    requiresAnyOf: [PERMISSIONS.PRESCRIPTION_READ],
  },
];

@Component({
  selector: 'app-patient-chart-nav',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './patient-chart-nav.html',
  styleUrl: './patient-chart-nav.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'patient-chart-nav' },
})
export class PatientChartNav {
  private readonly auth = inject(AuthService);

  readonly patientId = input.required<string>();

  protected readonly items = computed(() =>
    ITEMS.filter(
      (item) => !item.requiresAnyOf || item.requiresAnyOf.some((p) => this.auth.hasPermission(p)),
    ),
  );

  protected linkFor(item: ChartNavItem): unknown[] {
    return ['/patients', this.patientId(), item.segment];
  }
}
