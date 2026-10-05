import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import { catchError, of } from 'rxjs';
import { type AbstractControl, FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { Step, StepList, StepPanel, StepPanels, Stepper } from 'primeng/stepper';
import { Select } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { DatePicker } from 'primeng/datepicker';
import { Textarea } from 'primeng/textarea';
import { ButtonDirective } from 'primeng/button';
import { AutoComplete, type AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { FhirIntegrationNote } from '../../components/fhir-integration-note/fhir-integration-note';
import { ImagingSafetyForm } from '../../components/imaging-safety-form/imaging-safety-form';
import { FormField } from '../../components/form-field/form-field';
import { PageHeader } from '../../components/page-header/page-header';
import { SlotPicker } from '../../components/slot-picker/slot-picker';
import { SummaryList, type SummaryItem } from '../../components/summary-list/summary-list';
import { WizardStepFooter } from '../../components/wizard-step-footer/wizard-step-footer';
import {
  IMAGING_MODALITY_OPTIONS,
  LATERALITY_OPTIONS,
  URGENCY_OPTIONS,
} from '../../constants/labels';
import type { HasUnsavedChanges } from '../../guards/unsaved-changes.guard';
import type { ImagingExam, ScheduleSlot } from '../../models';
import type { FieldError } from '../../models/api';
import { toApiError } from '../../utils/api-error';
import type { DiagnosisOption } from '../../utils/diagnosis-options';
import { toLocalIsoDate } from '../../utils/date-utils';
import {
  bindStep1Rules,
  createStep1Form,
  createStep2Form,
  createStep3Form,
  createStep4Form,
  findNearestSlot,
} from './imaging-order-wizard.forms';
import {
  buildImagingOrderCreateRequest,
  buildImagingSummary,
  injectImagingPatientData,
  createSafetyState,
} from './imaging-order-wizard.helpers';
import { orderSubmitObserver, warnIncompleteOrder } from '../../utils/order-wizard';
import { rawValueSignal } from '../../utils/form-signals';
import { tryAdvance } from '../../utils/wizard';
import { PatientContextService } from '../../services/patient-context.service';
import { ImagingOrderService } from '../../services/imaging-order.service';

@Component({
  selector: 'app-imaging-order-wizard-page',
  imports: [
    PageHeader,
    Stepper,
    StepList,
    Step,
    StepPanels,
    StepPanel,
    WizardStepFooter,
    ReactiveFormsModule,
    Select,
    SelectButton,
    ToggleSwitch,
    DatePicker,
    Textarea,
    AutoComplete,
    FormField,
    ImagingSafetyForm,
    SummaryList,
    FhirIntegrationNote,
    SlotPicker,
    ButtonDirective,
  ],
  templateUrl: './imaging-order-wizard-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'imaging-order-wizard-page' },
})
export class ImagingOrderWizardPage implements HasUnsavedChanges {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly imagingOrderService = inject(ImagingOrderService);
  private readonly ctx = inject(PatientContextService);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);

  readonly patientId = input.required<string>();

  protected readonly submitted = signal(false);
  protected readonly submitting = signal(false);

  protected readonly modalityOptions = IMAGING_MODALITY_OPTIONS;
  protected readonly lateralityOptions = LATERALITY_OPTIONS;
  protected readonly urgencyOptions = URGENCY_OPTIONS;

  // ---- Data resources ----
  private readonly catalogResource = rxResource({
    stream: () =>
      this.imagingOrderService.getCatalog().pipe(
        catchError(() => {
          this.toast.add({
            severity: 'error',
            summary: 'Katalog badań',
            detail: 'Nie udało się wczytać katalogu badań obrazowych.',
          });
          return of<ImagingExam[]>([]);
        }),
      ),
  });
  protected readonly fullCatalog = computed<ImagingExam[]>(
    () => this.catalogResource.value() ?? [],
  );

  private readonly patientData = injectImagingPatientData(this.patientId);
  protected readonly diagnosisOptions = this.patientData.diagnosisOptions;
  protected readonly hasContrastAllergy = this.patientData.hasContrastAllergy;
  protected readonly latestCreatinineEgfr = this.patientData.latestCreatinineEgfr;
  protected readonly allergiesUnavailable = this.patientData.allergiesUnavailable;
  protected readonly labResultsUnavailable = this.patientData.labResultsUnavailable;
  protected readonly contrastSafetyDataUnavailable = this.patientData.contrastSafetyDataUnavailable;

  protected searchDiagnosis(event: AutoCompleteCompleteEvent): void {
    this.patientData.searchDiagnosis(event.query);
  }

  protected readonly step1Form = createStep1Form(this.fb);

  // Reactive forms are not signals: derive computeds from value-change signals (steps 2/4 below).
  private readonly step1Value = rawValueSignal(this.step1Form);

  protected readonly examsForModality = computed<ImagingExam[]>(() => {
    const modality = this.step1Value().modality;
    if (!modality) return [];
    return this.fullCatalog().filter((e) => e.modality === modality);
  });

  protected readonly selectedExam = computed<ImagingExam | null>(() => {
    const code = this.step1Value().examCode;
    return this.fullCatalog().find((e) => e.code === code) ?? null;
  });

  constructor() {
    // Plain lookup (not the signal): runs inside the control's valueChanges, before the group emits.
    bindStep1Rules(
      this.step1Form,
      () =>
        this.fullCatalog().find((e) => e.code === this.step1Form.controls.examCode.value) ?? null,
    );

    // Prefill "contrast allergy" once the patient's allergy data resolves.
    effect(() => {
      if (this.hasContrastAllergy() && !this.step3Form.controls.contrastAllergy.value) {
        this.step3Form.controls.contrastAllergy.setValue(true);
      }
    });
  }

  // ---- Step 2: Wskazania kliniczne ----
  protected readonly step2Form = createStep2Form(this.fb);
  private readonly step2Value = rawValueSignal(this.step2Form);

  // ---- Step 3: Bezpieczeństwo pacjenta ----
  protected readonly step3Form = createStep3Form(this.fb);

  private readonly safety = createSafetyState({
    step1Form: this.step1Form,
    step3Form: this.step3Form,
    patient: this.ctx.patient,
    egfr: this.latestCreatinineEgfr,
    contrastSafetyDataUnavailable: this.contrastSafetyDataUnavailable,
  });
  protected readonly needsPregnancyCheck = this.safety.needsPregnancyCheck;
  protected readonly isMri = this.safety.isMri;
  protected readonly egfrBlocksContrast = this.safety.egfrBlocksContrast;
  protected readonly contrastSafetyUnverified = this.safety.contrastSafetyUnverified;
  protected readonly step3Blocked = this.safety.step3Blocked;

  // ---- Step 4: Termin badania ----
  protected readonly step4Form = createStep4Form(this.fb);
  private readonly step4Value = rawValueSignal(this.step4Form);

  protected readonly selectedSlot = signal<ScheduleSlot | null>(null);
  /** Bumped to make the slot picker refetch after a slot conflict. */
  protected readonly slotRefresh = signal(0);

  protected readonly isCito = computed(() => this.step2Value().urgency === 'stat');

  protected readonly slotDateIso = computed(() => toLocalIsoDate(this.step4Value().date));

  protected onSlotSelected(slot: ScheduleSlot): void {
    this.selectedSlot.set(slot);
    this.step4Form.controls.slotId.setValue(slot.id);
    this.step4Form.controls.slotId.markAsDirty();
    this.step4Form.controls.immediate.setValue(false);
  }

  protected toggleImmediate(): void {
    this.step4Form.controls.immediate.setValue(true);
    this.step4Form.controls.slotId.setValue(null);
    this.selectedSlot.set(null);
  }

  protected findNearestSlot(): void {
    const modality = this.step1Form.controls.modality.value;
    if (!modality) return;
    findNearestSlot(
      (m, iso) => this.imagingOrderService.getSlots(m, iso),
      modality,
      (date, slot) => {
        this.step4Form.controls.date.setValue(date);
        this.onSlotSelected(slot);
      },
      () =>
        this.toast.add({
          severity: 'error',
          summary: 'Najbliższy termin',
          detail: 'Nie udało się wyszukać najbliższego terminu.',
        }),
    );
  }

  // ---- Step 5: Summary ----
  protected readonly summaryItems = computed<SummaryItem[]>(() =>
    buildImagingSummary({
      exam: this.selectedExam(),
      modality: this.step1Value().modality,
      laterality: this.step1Value().laterality,
      contrast: this.step1Value().contrast,
      urgency: this.step2Value().urgency,
      diagnosis: this.selectedDiagnosis(),
      clinicalIndication: this.step2Value().clinicalIndication,
      immediate: this.step4Value().immediate,
      slot: this.selectedSlot(),
    }),
  );

  private selectedDiagnosis(): DiagnosisOption | undefined {
    return this.step2Value().diagnosisCode ?? undefined;
  }

  hasUnsavedChanges(): boolean {
    if (this.submitted()) return false;
    return (
      this.step1Form.dirty || this.step2Form.dirty || this.step3Form.dirty || this.step4Form.dirty
    );
  }

  protected advanceFromStep1(activate: (value: number) => void): void {
    tryAdvance(this.step1Form, activate, 2);
  }

  protected advanceFromStep2(activate: (value: number) => void): void {
    tryAdvance(this.step2Form, activate, 3);
  }

  protected advanceFromStep3(activate: (value: number) => void): void {
    this.step3Form.markAllAsTouched();
    this.step3Form.updateValueAndValidity();
    if (this.contrastSafetyUnverified()) {
      this.toast.add({
        severity: 'error',
        summary: 'Nie można kontynuować',
        detail:
          'Nie udało się zweryfikować bezpieczeństwa kontrastu - nie można złożyć zlecenia z kontrastem, dopóki dane nie zostaną wczytane poprawnie. Odśwież stronę, usuń kontrast ze zlecenia, albo zaznacz pole „Uczulenie na środek kontrastowy” w kroku 3, jeśli u pacjenta faktycznie występuje uczulenie.',
      });
      return;
    }
    if (this.step3Blocked()) {
      this.toast.add({
        severity: 'warn',
        summary: 'Nie można kontynuować',
        detail: 'Sprawdź przeciwwskazania bezpieczeństwa przed przejściem dalej.',
      });
      return;
    }
    tryAdvance(this.step3Form, activate, 4);
  }

  protected advanceFromStep4(activate: (value: number) => void): void {
    tryAdvance(this.step4Form, activate, 5);
  }

  protected submit(): void {
    this.step3Form.markAllAsTouched();
    this.step2Form.markAllAsTouched();
    this.step1Form.markAllAsTouched();
    if (
      this.step1Form.invalid ||
      this.step2Form.invalid ||
      this.step3Form.invalid ||
      this.step3Blocked()
    ) {
      warnIncompleteOrder(this.toast);
      return;
    }

    const exam = this.selectedExam();
    if (!exam) return;
    this.submitting.set(true);
    this.imagingOrderService
      .createOrder(
        buildImagingOrderCreateRequest({
          patientId: this.patientId(),
          exam,
          laterality: this.step1Form.controls.laterality.value,
          contrast: this.step1Form.controls.contrast.value,
          clinicalIndication: this.step2Form.controls.clinicalIndication.value,
          clinicalQuestion: this.step2Form.controls.clinicalQuestion.value,
          diagnosis: this.selectedDiagnosis(),
          urgency: this.step2Form.controls.urgency.value,
          slot: this.selectedSlot(),
          safety: this.step3Form.getRawValue(),
          creatinineEgfr: this.latestCreatinineEgfr(),
        }),
      )
      .subscribe(
        orderSubmitObserver({
          submitting: this.submitting,
          submitted: this.submitted,
          toast: this.toast,
          router: this.router,
          patientId: this.patientId(),
          orderType: 'imaging',
          isCito: this.isCito(),
          successDetail: 'Zlecenie badania obrazowego zostało zapisane.',
          onError: (error) => this.handleSubmitError(error),
        }),
      );
  }

  /** 409 = slot taken meanwhile (drop it, refetch); 422 `errors[]` land on form fields. */
  private handleSubmitError(error: unknown): boolean {
    const api = toApiError(error);
    if (api.status === 409 && this.selectedSlot()) {
      this.step4Form.controls.slotId.setValue(null);
      this.selectedSlot.set(null);
      this.slotRefresh.update((n) => n + 1);
      this.toast.add({
        severity: 'error',
        summary: 'Termin jest już zajęty',
        detail: 'Wybierz inny termin badania.',
      });
      return true;
    }
    return this.applyServerErrors(api.fieldErrors);
  }

  /** Puts 422 `errors[]` on matching fields; every message also goes to the toast (steps differ). */
  private applyServerErrors(errors: FieldError[]): boolean {
    if (errors.length === 0) return false;
    const controls: Record<string, AbstractControl> = {
      examCode: this.step1Form.controls.examCode,
      laterality: this.step1Form.controls.laterality,
      contrast: this.step1Form.controls.contrast,
      clinicalIndication: this.step2Form.controls.clinicalIndication,
      clinicalQuestion: this.step2Form.controls.clinicalQuestion,
      urgency: this.step2Form.controls.urgency,
      'safety.confirmed': this.step3Form.controls.confirmed,
      slotId: this.step4Form.controls.slotId,
    };
    for (const e of errors) {
      const control = controls[e.field];
      if (!control) continue;
      control.setErrors({ server: e.message });
      control.markAsTouched();
    }
    this.toast.add({
      severity: 'error',
      summary: 'Nie udało się wysłać zlecenia',
      detail: errors.map((e) => e.message).join(' '),
    });
    return true;
  }

  protected cancel(): void {
    void this.router.navigate(['/patients', this.patientId(), 'orders']);
  }
}
