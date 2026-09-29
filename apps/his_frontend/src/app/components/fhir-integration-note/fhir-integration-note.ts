import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Message } from 'primeng/message';

@Component({
  selector: 'app-fhir-integration-note',
  imports: [Message],
  templateUrl: './fhir-integration-note.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FhirIntegrationNote {
  readonly resource = input.required<'ServiceRequest' | 'MedicationRequest' | 'Observation'>();

  protected readonly text = computed(
    () =>
      `Integracja z serwisem FHIR (${this.resource()}) planowana — dane zapisywane lokalnie (tryb demonstracyjny).`,
  );
}
