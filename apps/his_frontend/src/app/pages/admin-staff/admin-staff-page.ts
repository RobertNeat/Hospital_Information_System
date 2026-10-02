import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ConfirmationService, MessageService } from 'primeng/api';
import { Tooltip } from 'primeng/tooltip';
import { DataTable } from '../../components/data-table/data-table';
import { PageHeader } from '../../components/page-header/page-header';
import { StatusTag } from '../../components/status-tag/status-tag';
import { LabelPipe } from '../../pipes/label.pipe';
import type { StaffMember, TableColumn } from '../../models';
import { toApiError } from '../../utils/api-error';
import { AuthService } from '../../services/auth.service';
import { StaffService } from '../../services/staff.service';
import { PERMISSIONS } from '../../constants/permissions';

/**
 * Admin-only staff directory: activate (also unlocks) or lock an account.
 * Backend: `POST /staff/{id}/activate` and `/lock` (no request body, require `account:manage`);
 * locking one's own account is rejected with 409.
 */
@Component({
  selector: 'app-admin-staff-page',
  imports: [PageHeader, DataTable, StatusTag, LabelPipe, Tooltip],
  templateUrl: './admin-staff-page.html',
  styleUrl: './admin-staff-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'admin-staff-page' },
})
export class AdminStaffPage {
  private readonly staffService = inject(StaffService);
  private readonly auth = inject(AuthService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly toast = inject(MessageService);

  protected readonly canManageAccounts = computed(() =>
    this.auth.hasPermission(PERMISSIONS.ACCOUNT_MANAGE),
  );

  protected readonly rows = this.staffService.staff;
  private readonly loadingState = signal(true);
  protected readonly loading = this.loadingState.asReadonly();

  protected readonly columns: TableColumn<StaffMember>[] = [
    { field: 'lastName', header: 'Nazwisko i imię', sortable: true },
    { field: 'role', header: 'Rola' },
    { field: 'employeeId', header: 'Identyfikator' },
    { field: 'email', header: 'E-mail' },
    { field: 'accountStatus', header: 'Status konta', type: 'tag', tagKind: 'accountStatus' },
  ];

  constructor() {
    // Force a refetch: the cache may be stale (filled by `nameOf()` earlier in the session)
    // or missing accounts that registered since, and activating new accounts is this page's job.
    this.staffService
      .load(true)
      .pipe(finalize(() => this.loadingState.set(false)))
      .subscribe({
        error: () =>
          this.toast.add({ severity: 'error', summary: 'Nie udało się pobrać listy pracowników' }),
      });
  }

  /** Activate (also unlocks) only applies to an account that exists and isn't already active. */
  protected canActivate(row: StaffMember): boolean {
    return (
      this.canManageAccounts() &&
      (row.accountStatus === 'pending' || row.accountStatus === 'locked')
    );
  }

  /** Locking one's own account is rejected by the backend (409), so hide it here too. */
  protected canLock(row: StaffMember): boolean {
    return (
      this.canManageAccounts() &&
      row.accountStatus === 'active' &&
      row.id !== this.staffService.currentUser().id
    );
  }

  protected requestActivate(row: StaffMember): void {
    this.confirmationService.confirm({
      header: 'Aktywuj konto',
      message: `Czy na pewno aktywować konto ${row.firstName} ${row.lastName}?`,
      acceptLabel: 'Aktywuj',
      rejectLabel: 'Anuluj',
      accept: () => this.activate(row),
    });
  }

  protected requestLock(row: StaffMember): void {
    this.confirmationService.confirm({
      header: 'Zablokuj konto',
      message: `Czy na pewno zablokować konto ${row.firstName} ${row.lastName}?`,
      acceptLabel: 'Zablokuj',
      rejectLabel: 'Anuluj',
      accept: () => this.lock(row),
    });
  }

  private activate(row: StaffMember): void {
    this.staffService.activate(row.id).subscribe({
      next: () => this.toast.add({ severity: 'success', summary: 'Konto aktywowane' }),
      error: (error: unknown) =>
        this.toast.add({
          severity: 'error',
          summary: 'Nie udało się aktywować konta',
          detail: toApiError(error).problem.detail,
        }),
    });
  }

  private lock(row: StaffMember): void {
    this.staffService.lock(row.id).subscribe({
      next: () => this.toast.add({ severity: 'success', summary: 'Konto zablokowane' }),
      error: (error: unknown) =>
        this.toast.add({
          severity: 'error',
          summary: 'Nie udało się zablokować konta',
          detail: toApiError(error).problem.detail,
        }),
    });
  }
}
