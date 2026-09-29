import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { PageHeader } from '../../components/page-header/page-header';
import { PatientSearch } from '../../components/patient-search/patient-search';
import { EmptyState } from '../../components/empty-state/empty-state';
import { PatientContextService } from '../../services/patient-context.service';
import { AgePipe } from '../../pipes/age.pipe';
import { FullNamePipe } from '../../pipes/full-name.pipe';
import type { PatientSummary } from '../../models';

@Component({
  selector: 'app-patient-picker-page',
  imports: [PageHeader, PatientSearch, EmptyState, AgePipe, FullNamePipe],
  templateUrl: './patient-picker-page.html',
  styleUrl: './patient-picker-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatientPickerPage {
  private readonly router = inject(Router);
  protected readonly ctx = inject(PatientContextService);

  /** The child segment to continue to, e.g. 'history' or 'orders/lab/new'. Defaults to 'overview'. */
  readonly next = input<string>('overview');

  protected goTo(patient: PatientSummary): void {
    const segment = this.next() || 'overview';
    // `next` may contain slashes (e.g. 'orders/lab/new') -- build the URL directly
    // rather than passing segments through `router.navigate`, which would encode them.
    this.router.navigateByUrl(`/patients/${patient.id}/${segment}`);
  }
}
