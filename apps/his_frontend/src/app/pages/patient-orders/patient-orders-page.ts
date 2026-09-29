import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ButtonDirective } from 'primeng/button';
import { ConfirmationService, MessageService } from 'primeng/api';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';
import { Textarea } from 'primeng/textarea';
import { TableModule } from 'primeng/table';
import { Tooltip } from 'primeng/tooltip';
import { EmptyState } from '../../components/empty-state/empty-state';
import { PageHeader } from '../../components/page-header/page-header';
import { StatusTag } from '../../components/status-tag/status-tag';
import { OrderStatusTimeline } from '../../components/order-status-timeline/order-status-timeline';
import { LabelPipe } from '../../pipes/label.pipe';
import type { ImagingOrder, LabOrder } from '../../models';
import { LabOrderService } from '../../services/lab-order.service';
import { ImagingOrderService } from '../../services/imaging-order.service';

type OrdersTab = 'lab' | 'imaging';

@Component({
  selector: 'app-patient-orders-page',
  imports: [
    PageHeader,
    EmptyState,
    RouterLink,
    ButtonDirective,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    TableModule,
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
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toast = inject(MessageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly patientId = input.required<string>();
  readonly type = input<'lab' | 'imaging'>();

  protected readonly activeTab = computed<OrdersTab>(() =>
    this.type() === 'imaging' ? 'imaging' : 'lab',
  );

  protected readonly cancelReason = signal('');
  private readonly cancelTargetId = signal<string | null>(null);
  private readonly cancelTargetKind = signal<OrdersTab>('lab');

  private readonly labResource = rxResource({
    params: () => this.patientId(),
    stream: ({ params: pid }) => this.labOrderService.getOrders({ patientId: pid }),
  });

  private readonly imagingResource = rxResource({
    params: () => this.patientId(),
    stream: ({ params: pid }) => this.imagingOrderService.getOrders({ patientId: pid }),
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

  protected canCancel(status: string): boolean {
    return status !== 'completed' && status !== 'cancelled';
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
    const onCancelled = () => {
      this.toast.add({
        severity: 'success',
        summary: 'Zlecenie anulowane',
      });
      if (kind === 'lab') this.labResource.reload();
      else this.imagingResource.reload();
    };
    if (kind === 'lab') {
      this.labOrderService.cancelOrder(id, reason).subscribe(onCancelled);
    } else {
      this.imagingOrderService.cancelOrder(id, reason).subscribe(onCancelled);
    }
  }
}
