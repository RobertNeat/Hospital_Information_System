import type { Observable } from 'rxjs';
import { catchError, of } from 'rxjs';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Button } from 'primeng/button';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { DataTable } from '../../components/data-table/data-table';
import { EmptyState } from '../../components/empty-state/empty-state';
import { PageHeader } from '../../components/page-header/page-header';
import { StatusTag } from '../../components/status-tag/status-tag';
import { OrderStatusTimeline } from '../../components/order-status-timeline/order-status-timeline';
import { LabelPipe } from '../../pipes/label.pipe';
import type { ImagingOrder, LabOrder, TableColumn } from '../../models';
import { toApiError } from '../../utils/api-error';
import { AuthService } from '../../services/auth.service';
import { LabOrderService } from '../../services/lab-order.service';
import { ImagingOrderService } from '../../services/imaging-order.service';
import { PERMISSIONS } from '../../constants/permissions';

type OrdersTab = 'lab' | 'imaging';

@Component({
  selector: 'app-patient-orders-page',
  imports: [
    PageHeader,
    EmptyState,
    RouterLink,
    Button,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    DataTable,
    StatusTag,
    OrderStatusTimeline,
    ConfirmDialog,
    Textarea,
    FormsModule,
    LabelPipe,
    DatePipe,
    Tooltip,
  ],
  templateUrl: './patient-orders-page.html',
  styleUrl: './patient-orders-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'patient-orders-page' },
})
export class PatientOrdersPage {
  private readonly labOrderService = inject(LabOrderService);
  private readonly imagingOrderService = inject(ImagingOrderService);
  private readonly auth = inject(AuthService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toast = inject(MessageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly patientId = input.required<string>();
  readonly type = input<'lab' | 'imaging'>();

  /** Hides "Nowe zlecenie..." buttons the current role has no permission for. */
  protected readonly canCreateLabOrder = computed(() =>
    this.auth.hasPermission(PERMISSIONS.LAB_ORDER_CREATE),
  );
  protected readonly canCreateImagingOrder = computed(() =>
    this.auth.hasPermission(PERMISSIONS.IMAGING_ORDER_CREATE),
  );

  /** Hides the "Laboratoryjne"/"Obrazowe" tab the role has no read access to (e.g. radiologist
   *  has no lab-order:read) -- otherwise a role without a type would land on it by default and
   *  see a misleading "Brak zleceń ..." (empty) instead of simply never seeing the tab. */
  protected readonly canSeeLab = computed(() =>
    this.auth.hasPermission(PERMISSIONS.LAB_ORDER_READ),
  );
  protected readonly canSeeImaging = computed(() =>
    this.auth.hasPermission(PERMISSIONS.IMAGING_ORDER_READ),
  );

  protected readonly activeTab = computed<OrdersTab>(() => {
    const requested = this.type();
    if (requested === 'imaging' && this.canSeeImaging()) return 'imaging';
    if (requested === 'lab' && this.canSeeLab()) return 'lab';
    // No (valid) requested tab -- default to the first one this role can see.
    return this.canSeeLab() ? 'lab' : 'imaging';
  });

  protected readonly labColumns: TableColumn<LabOrder>[] = [
    { field: 'orderedAt', header: 'Data zlecenia' },
    { field: 'items', header: 'Badania' },
    { field: 'urgency', header: 'Pilność' },
    { field: 'status', header: 'Status' },
  ];
  protected readonly imagingColumns: TableColumn<ImagingOrder>[] = [
    { field: 'orderedAt', header: 'Data zlecenia' },
    { field: 'examName', header: 'Badanie' },
    { field: 'urgency', header: 'Pilność' },
    { field: 'status', header: 'Status' },
  ];

  protected readonly cancelReason = signal('');
  private readonly cancelTargetId = signal<string | null>(null);
  private readonly cancelTargetKind = signal<OrdersTab>('lab');

  // `params: () => undefined` leaves a resource idle (no request, no load) -- used here so a
  // role without read access (e.g. radiologist has no lab-order:read) never sends the call that
  // the backend would reject with 403 in the first place; that 403 is expected, not an error to
  // report. A failure on a call the role *is* allowed to make is a genuine problem: `catchError`
  // reports it via toast and resolves to `[]` so the resource stays "resolved" rather than
  // "error" (an errored resource's `.value()` throws, which would otherwise freeze the view --
  // reported: radiologist without lab-order:read hit a stuck spinner on /orders?type=imaging).
  private readonly labResource = rxResource({
    params: () =>
      this.auth.hasPermission(PERMISSIONS.LAB_ORDER_READ) ? this.patientId() : undefined,
    stream: ({ params: pid }) =>
      this.labOrderService.getOrders({ patientId: pid }).pipe(
        catchError((err: unknown) => {
          this.toast.add({
            severity: 'error',
            summary: 'Nie udało się wczytać zleceń laboratoryjnych',
            detail: toApiError(err).problem.detail,
          });
          return of<LabOrder[]>([]);
        }),
      ),
  });

  private readonly imagingResource = rxResource({
    params: () =>
      this.auth.hasPermission(PERMISSIONS.IMAGING_ORDER_READ) ? this.patientId() : undefined,
    stream: ({ params: pid }) =>
      this.imagingOrderService.getOrders({ patientId: pid }).pipe(
        catchError((err: unknown) => {
          this.toast.add({
            severity: 'error',
            summary: 'Nie udało się wczytać zleceń obrazowych',
            detail: toApiError(err).problem.detail,
          });
          return of<ImagingOrder[]>([]);
        }),
      ),
  });

  protected readonly labOrders = computed<LabOrder[]>(() =>
    [...(this.labResource.value() ?? [])].sort((a, b) => b.orderedAt.localeCompare(a.orderedAt)),
  );
  protected readonly imagingOrders = computed<ImagingOrder[]>(() =>
    [...(this.imagingResource.value() ?? [])].sort((a, b) =>
      b.orderedAt.localeCompare(a.orderedAt),
    ),
  );

  protected readonly labLoading = computed(() => this.labResource.isLoading());
  protected readonly imagingLoading = computed(() => this.imagingResource.isLoading());

  protected onTabChange(value: string | number | undefined): void {
    if (value === undefined) return;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { type: String(value) },
      queryParamsHandling: 'merge',
    });
  }

  /** Cancelling lab and imaging orders are separate (doctor-only) permissions on the backend. */
  protected canCancel(status: string, kind: OrdersTab = 'lab'): boolean {
    if (status === 'completed' || status === 'cancelled') return false;
    return this.auth.hasPermission(kind === 'lab' ? 'lab-order:cancel' : 'imaging-order:cancel');
  }

  protected requestCancel(kind: OrdersTab, id: string): void {
    this.cancelTargetId.set(id);
    this.cancelTargetKind.set(kind);
    this.cancelReason.set('');
    this.confirmationService.confirm({
      key: 'cancel-order',
      header: 'Anuluj zlecenie',
      message: 'Podaj powód anulowania zlecenia.',
      acceptLabel: 'Anuluj zlecenie',
      rejectLabel: 'Wróć',
      accept: () => this.confirmCancel(),
    });
  }

  private confirmCancel(): void {
    const id = this.cancelTargetId();
    const reason = this.cancelReason().trim();
    if (!id) return;
    if (!reason) {
      this.toast.add({
        severity: 'warn',
        summary: 'Podaj powód anulowania',
        detail: 'Powód anulowania zlecenia jest wymagany.',
      });
      return;
    }
    const kind = this.cancelTargetKind();
    const resource = kind === 'lab' ? this.labResource : this.imagingResource;
    const version = resource.value()?.find((o) => o.id === id)?.version;
    const cancel$: Observable<unknown> =
      kind === 'lab'
        ? this.labOrderService.cancelOrder(id, reason, version)
        : this.imagingOrderService.cancelOrder(id, reason, version);
    cancel$.subscribe({
      next: () => {
        this.toast.add({ severity: 'success', summary: 'Zlecenie anulowane' });
        resource.reload();
      },
      // Reload after a failure too: a 409 means the loaded status/version is stale.
      error: (error: unknown) => {
        this.toast.add({
          severity: 'error',
          summary: 'Nie udało się anulować zlecenia',
          detail: toApiError(error).problem.detail,
        });
        resource.reload();
      },
    });
  }
}
