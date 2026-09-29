import { ChangeDetectionStrategy, Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AutoComplete,
  type AutoCompleteCompleteEvent,
  type AutoCompleteSelectEvent,
} from 'primeng/autocomplete';
import { PatientService } from '../../services/patient.service';
import type { PatientSummary } from '../../models';
import { AgePipe } from '../../pipes/age.pipe';
import { FullNamePipe } from '../../pipes/full-name.pipe';

@Component({
  selector: 'app-patient-search',
  imports: [FormsModule, AutoComplete, AgePipe, FullNamePipe],
  templateUrl: './patient-search.html',
  styleUrl: './patient-search.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'patient-search' },
})
export class PatientSearch {
  private readonly patientService = inject(PatientService);

  readonly placeholder = input('Szukaj: nazwisko, PESEL, nr historii');

  readonly patientSelected = output<PatientSummary>();

  protected readonly selectedValue = signal<PatientSummary | null>(null);
  protected readonly suggestions = signal<PatientSummary[]>([]);

  protected search(event: AutoCompleteCompleteEvent): void {
    const term = (event.query ?? '').trim();
    if (!term) {
      this.suggestions.set([]);
      return;
    }
    this.patientService.search(term).subscribe((results) => this.suggestions.set(results));
  }

  protected onSelect(event: AutoCompleteSelectEvent): void {
    this.patientSelected.emit(event.value as PatientSummary);
    // Reset so the field is ready for the next lookup instead of "sticking" on the pick.
    queueMicrotask(() => this.selectedValue.set(null));
  }
}
