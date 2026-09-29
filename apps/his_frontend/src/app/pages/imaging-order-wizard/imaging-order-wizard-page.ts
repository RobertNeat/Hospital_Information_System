import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators, type AbstractControl } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { MessageService } from 'primeng/api';
import { Step, StepList, StepPanel, StepPanels, Stepper } from 'primeng/stepper';
import { Select } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { Checkbox } from 'primeng/checkbox';
import { DatePicker } from 'primeng/datepicker';
import { Textarea } from 'primeng/textarea';
import { Message } from 'primeng/message';
import { ButtonDirective } from 'primeng/button';
import { FhirIntegrationNote } from '../../components/fhir-integration-note/fhir-integration-note';
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
import type {
  Coding,
  ImagingExam,
  ImagingModality,
  Laterality,
  OrderUrgency,
  ScheduleSlot,
} from '../../models';
import { ageFromBirthDate } from '../../utils/date-utils';
import { tryAdvance } from '../../utils/wizard';
import { PatientContextService } from '../../services/patient-context.service';
import { EhrService } from '../../services/ehr.service';
import { ImagingOrderService } from '../../services/imaging-order.service';
import { LabResultService } from '../../services/lab-result.service';
import { StaffService } from '../../services/staff.service';

interface DiagnosisOption {
  label: string;
  value: string;
  coding: Coding;
}

const PREGNANCY_MODALITIES: ImagingModality[] = ['RTG', 'CT', 'MMG', 'ANGIOGRAPHY'];

/** ATC group for iodine/gadolinium contrast media allergies (mock-data convention). */
const CONTRAST_ATC_PREFIX = 'V08';

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
    Checkbox,
    DatePicker,
    Textarea,
    Message,
    FormField,
    SummaryList,
    FhirIntegrationNote,
    SlotPicker,
    ButtonDirective,
    DatePipe,
  ],
  templateUrl: './imaging-order-wizard-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImagingOrderWizardPage implements HasUnsavedChanges {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly ehrService = inject(EhrService);
  private readonly imagingOrderService = inject(ImagingOrderService);
  private readonly labResultService = inject(LabResultService);
  private readonly staffService = inject(StaffService);
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
    stream: () => this.imagingOrderService.getCatalog(),
  });
  protected readonly fullCatalog = computed<ImagingExam[]>(
    () => this.catalogResource.value() ?? [],
  );

  private readonly diagnosesResource = rxResource({
    params: () => this.patientId(),
    stream: ({ params: pid }) =>
      forkJoin({
        diagnoses: this.ehrService.getDiagnoses(pid),
        icd10: this.ehrService.getIcd10Dictionary(),
        allergies: this.ehrService.getAllergies(pid),
        labResults: this.labResultService.getResults(pid),
      }),
  });

  protected readonly diagnosisOptions = computed<DiagnosisOption[]>(() => {
    const data = this.diagnosesResource.value();
    if (!data) return [];
    const seen = new Set<string>();
    const options: DiagnosisOption[] = [];
    for (const d of data.diagnoses) {
      if (seen.has(d.code.code)) continue;
      seen.add(d.code.code);
      options.push({
        label: `${d.code.code} — ${d.code.display}`,
        value: d.code.code,
        coding: d.code,
      });
    }
    for (const c of data.icd10) {
      if (seen.has(c.code)) continue;
      seen.add(c.code);
      options.push({ label: `${c.code} — ${c.display}`, value: c.code, coding: c });
    }
    return options;
  });

  protected readonly hasContrastAllergy = computed(() => {
    const data = this.diagnosesResource.value();
    if (!data) return false;
    return data.allergies.some(
      (a) => a.status === 'active' && a.atcCodes?.some((c) => c.startsWith(CONTRAST_ATC_PREFIX)),
    );
  });

  protected readonly latestCreatinineEgfr = computed<{
    creatinine?: number;
    egfr?: number;
    collectedAt?: string;
  } | null>(() => {
    const data = this.diagnosesResource.value();
    if (!data) return null;
    const withCrea = data.labResults
      .filter((r) =>
        r.observations.some((o) => o.analyteCode === 'EGFR' || o.analyteCode === 'KREA'),
      )
      .sort((a, b) => b.collectedAt.localeCompare(a.collectedAt));
    const latest = withCrea[0];
    if (!latest) return null;
    const crea = latest.observations.find((o) => o.analyteCode === 'KREA');
    const egfr = latest.observations.find((o) => o.analyteCode === 'EGFR');
    return {
      creatinine: typeof crea?.value === 'number' ? crea.value : undefined,
      egfr: typeof egfr?.value === 'number' ? egfr.value : undefined,
      collectedAt: latest.collectedAt,
    };
  });

  protected readonly patientAge = computed<number | null>(() => {
    const p = this.ctx.patient();
    return p ? ageFromBirthDate(p.birthDate) : null;
  });

  // ---- Step 1: Rodzaj badania ----
  protected readonly step1Form = this.fb.group({
    modality: this.fb.control<ImagingModality | null>(null, { validators: [Validators.required] }),
    examCode: this.fb.control<string | null>(null, { validators: [Validators.required] }),
    laterality: this.fb.control<Laterality>('na'),
    contrast: this.fb.control(false),
  });

  protected readonly examsForModality = computed<ImagingExam[]>(() => {
    const modality = this.step1Form.controls.modality.value;
    if (!modality) return [];
    return this.fullCatalog().filter((e) => e.modality === modality);
  });

  protected readonly selectedExam = computed<ImagingExam | null>(() => {
    const code = this.step1Form.controls.examCode.value;
    return this.fullCatalog().find((e) => e.code === code) ?? null;
  });

  constructor() {
    this.step1Form.controls.modality.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.step1Form.controls.examCode.setValue(null);
      this.step1Form.controls.contrast.setValue(false);
      this.step1Form.controls.laterality.setValue('na');
    });

    this.step1Form.controls.examCode.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      const exam = this.selectedExam();
      if (exam?.requiresLaterality) {
        this.step1Form.controls.laterality.setValidators([
          Validators.required,
          (c: AbstractControl) => (c.value === 'na' ? { required: true } : null),
        ]);
      } else {
        this.step1Form.controls.laterality.clearValidators();
        this.step1Form.controls.laterality.setValue('na');
      }
      this.step1Form.controls.laterality.updateValueAndValidity();
      if (!exam?.contrastPossible) {
        this.step1Form.controls.contrast.setValue(false);
      }
    });

    // Prefill "contrast allergy" once the patient's allergy data resolves.
    effect(() => {
      if (this.hasContrastAllergy() && !this.step3Form.controls.contrastAllergy.value) {
        this.step3Form.controls.contrastAllergy.setValue(true);
      }
    });
  }

  // ---- Step 2: Wskazania kliniczne ----
  protected readonly step2Form = this.fb.group({
    clinicalIndication: this.fb.control('', {
      validators: [Validators.required, Validators.minLength(20)],
    }),
    clinicalQuestion: this.fb.control(''),
    diagnosisCode: this.fb.control<string | null>(null),
    urgency: this.fb.control<OrderUrgency>('routine', { validators: [Validators.required] }),
  });

  // ---- Step 3: Bezpieczeństwo pacjenta ----
  protected readonly needsPregnancyCheck = computed(() => {
    const modality = this.step1Form.controls.modality.value;
    const age = this.patientAge();
    const gender = this.ctx.patient()?.gender;
    if (!modality || !PREGNANCY_MODALITIES.includes(modality)) return false;
    if (gender !== 'female') return false;
    if (age === null) return false;
    return age >= 12 && age <= 55;
  });

  protected readonly isMri = computed(() => this.step1Form.controls.modality.value === 'MRI');

  protected readonly egfrBlocksContrast = computed(() => {
    const egfr = this.latestCreatinineEgfr()?.egfr;
    return this.step1Form.controls.contrast.value && typeof egfr === 'number' && egfr < 30;
  });

  protected readonly step3Form = this.fb.group({
    pregnancy: this.fb.control<'no' | 'yes' | 'unknown' | 'na'>('na'),
    pacemakerOrImplant: this.fb.control(false),
    metalFragments: this.fb.control(false),
    contrastAllergy: this.fb.control(false),
    egfrConfirmed: this.fb.control(false),
    claustrophobia: this.fb.control(false),
    confirmed: this.fb.control(false, { validators: [Validators.requiredTrue] }),
  });

  protected readonly step3Blocked = computed(() => {
    if (
      this.isMri() &&
      (this.step3Form.controls.pacemakerOrImplant.value ||
        this.step3Form.controls.metalFragments.value)
    ) {
      return true;
    }
    if (this.egfrBlocksContrast() && !this.step3Form.controls.egfrConfirmed.value) {
      return true;
    }
    return false;
  });

  // ---- Step 4: Termin badania ----
  protected readonly step4Form = this.fb.group({
    date: this.fb.control<Date>(new Date()),
    slotId: this.fb.control<string | null>(null),
    immediate: this.fb.control(false),
  });

  protected readonly selectedSlot = signal<ScheduleSlot | null>(null);

  protected readonly isCito = computed(() => this.step2Form.controls.urgency.value === 'stat');

  protected readonly slotDateIso = computed(() => {
    const d = this.step4Form.controls.date.value;
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  });

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

  /** Scans forward up to 14 days for the first available slot of the selected modality. */
  protected findNearestSlot(): void {
    const modality = this.step1Form.controls.modality.value;
    if (!modality) return;
    const startDate = new Date();
    const tryDay = (offset: number): void => {
      if (offset > 14) return;
      const d = new Date(startDate);
      d.setDate(d.getDate() + offset);
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      const iso = `${y}-${m}-${day}`;
      this.imagingOrderService.getSlots(modality, iso).subscribe((slots) => {
        const now = new Date();
        const available = slots
          .filter((s) => s.available && new Date(s.start) > now)
          .sort((a, b) => a.start.localeCompare(b.start));
        if (available.length) {
          this.step4Form.controls.date.setValue(d);
          this.onSlotSelected(available[0]);
        } else {
          tryDay(offset + 1);
        }
      });
    };
    tryDay(0);
  }

  // ---- Step 5: Summary ----
  protected readonly summaryItems = computed<SummaryItem[]>(() => {
    const exam = this.selectedExam();
    const modalityLabel =
      IMAGING_MODALITY_OPTIONS.find((o) => o.value === this.step1Form.controls.modality.value)
        ?.label ?? '';
    const lateralityLabel =
      LATERALITY_OPTIONS.find((o) => o.value === this.step1Form.controls.laterality.value)?.label ??
      '';
    const urgencyLabel =
      URGENCY_OPTIONS.find((o) => o.value === this.step2Form.controls.urgency.value)?.label ?? '';
    const diagCode = this.step2Form.controls.diagnosisCode.value;
    const diag = this.diagnosisOptions().find((o) => o.value === diagCode);
    const slot = this.selectedSlot();
    return [
      { label: 'Badanie', value: exam ? `${exam.name} (${modalityLabel})` : '—' },
      { label: 'Strona', value: exam?.requiresLaterality ? lateralityLabel : '—' },
      { label: 'Kontrast', value: this.step1Form.controls.contrast.value ? 'Tak' : 'Nie' },
      { label: 'Pilność', value: urgencyLabel },
      { label: 'Rozpoznanie', value: diag?.label ?? '—' },
      { label: 'Wskazania kliniczne', value: this.step2Form.controls.clinicalIndication.value },
      {
        label: 'Termin',
        value: this.step4Form.controls.immediate.value
          ? 'Wykonanie natychmiastowe (bez terminu)'
          : slot
            ? `${new Date(slot.start).toLocaleString('pl-PL')} (${slot.room})`
            : '—',
      },
    ];
  });

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
      this.toast.add({
        severity: 'warn',
        summary: 'Uzupełnij wymagane pola',
        detail: 'Sprawdź wszystkie kroki formularza przed wysłaniem zlecenia.',
      });
      return;
    }

    const exam = this.selectedExam();
    if (!exam) return;
    const diagCode = this.step2Form.controls.diagnosisCode.value;
    const diag = this.diagnosisOptions().find((o) => o.value === diagCode);
    const slot = this.selectedSlot();

    this.submitting.set(true);
    this.imagingOrderService
      .createOrder({
        patientId: this.patientId(),
        examCode: exam.code,
        examName: exam.name,
        modality: exam.modality,
        bodyRegion: exam.bodyRegion,
        laterality: this.step1Form.controls.laterality.value,
        contrast: this.step1Form.controls.contrast.value,
        clinicalIndication: this.step2Form.controls.clinicalIndication.value,
        clinicalQuestion: this.step2Form.controls.clinicalQuestion.value || undefined,
        diagnosisCode: diag?.coding,
        urgency: this.step2Form.controls.urgency.value,
        safety: {
          pregnancy: this.step3Form.controls.pregnancy.value,
          pacemakerOrImplant: this.step3Form.controls.pacemakerOrImplant.value,
          metalFragments: this.step3Form.controls.metalFragments.value,
          contrastAllergy: this.step3Form.controls.contrastAllergy.value,
          creatinine: this.latestCreatinineEgfr()?.creatinine,
          egfr: this.latestCreatinineEgfr()?.egfr,
          claustrophobia: this.step3Form.controls.claustrophobia.value,
          confirmed: this.step3Form.controls.confirmed.value,
        },
        slotId: slot?.id,
        scheduledAt: slot?.start,
        orderedById: this.staffService.currentUser().id,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.submitted.set(true);
          const isCito = this.isCito();
          this.toast.add({
            severity: 'success',
            summary: isCito ? 'Zlecenie CITO wysłane' : 'Zlecenie wysłane',
            detail: 'Zlecenie badania obrazowego zostało zapisane.',
          });
          void this.router.navigate(['/patients', this.patientId(), 'orders'], {
            queryParams: { type: 'imaging' },
          });
        },
        error: () => {
          this.submitting.set(false);
          this.toast.add({ severity: 'error', summary: 'Nie udało się wysłać zlecenia' });
        },
      });
  }

  protected cancel(): void {
    void this.router.navigate(['/patients', this.patientId(), 'orders']);
  }
}
