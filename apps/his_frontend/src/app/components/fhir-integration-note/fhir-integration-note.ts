import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Message } from 'primeng/message';

@Component({
  selector: 'app-fhir-integration-note',
  imports: [Message],
  templateUrl: './fhir-integration-note.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'fhir-integration-note' },
})
export class FhirIntegrationNote {
  readonly resource = input.required<'ServiceRequest' | 'MedicationRequest' | 'Observation'>();

  /** Stan integracji (`his.integration.*.enabled`) nie jest znany frontendowi, więc tekst obejmuje oba przypadki. */
  protected readonly text = computed(
    () =>
      `Dane (${this.resource()}) są przekazywane do zewnętrznej usługi FHIR po zapisie; jeśli integracja jest wyłączona lub niedostępna, pozostają zapisane tylko lokalnie w HIS.`,
  );
}
