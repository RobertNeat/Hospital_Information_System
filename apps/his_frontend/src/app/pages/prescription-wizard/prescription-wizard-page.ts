import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { DatePicker } from 'primeng/datepicker';
import { InputNumber } from 'primeng/inputnumber';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { StepList, StepPanel, StepPanels, Step, Stepper } from 'primeng/stepper';
import { Textarea } from 'primeng/textarea';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { of, switchMap } from 'rxjs';
import { DosageEditor } from '../../components/dosage-editor/dosage-editor';
import { DrugPicker } from '../../components/drug-picker/drug-picker';
import { EPrescriptionDialog } from '../../components/e-prescription-dialog/e-prescription-dialog';
import { FhirIntegrationNote } from '../../components/fhir-integration-note/fhir-integration-note';
import { FormField } from '../../components/form-field/form-field';
import { PageHeader } from '../../components/page-header/page-header';
import { PrescriptionItemCard } from '../../components/prescription-item-card/prescription-item-card';
import { SummaryList, type SummaryItem } from '../../components/summary-list/summary-list';
import { WizardStepFooter } from '../../components/wizard-step-footer/wizard-step-footer';
import { REIMBURSEMENT_OPTIONS } from '../../constants/labels';
import type { HasUnsavedChanges } from '../../guards/unsaved-changes.guard';
import type {
  Drug,
  DrugSafetyWarning,
  Prescription,
  PrescriptionDraft,
  PrescriptionItem,
} from '../../models';
import { DrugService } from '../../services/drug.service';
import { PatientContextService } from '../../services/patient-context.service';
import { PrescriptionService } from '../../services/prescription.service';
import { StaffService } from '../../services/staff.service';
import { tryAdvance } from '../../utils/wizard';
import {
  computeDailyDose,
  suggestPackageQuantity,
  defaultRoute,
} from '../../components/dosage-editor/dosage-math';

const MAX_ITEMS = 5;
/**
 * "Long-term therapy" heuristic (not specified by the plan; documented per the agent brief):
 * a course is treated as long-term when its duration exceeds 90 days OR the clinician
 * explicitly marks it long-term via the "Leczenie przewlekłe" toggle in step 3. Long-term
 * prescriptions get a 365-day validity window instead of the standard 30 days, matching
 * Polish e-prescription practice for chronic medication.
 */
const LONG_TERM_DURATION_THRESHOLD_DAYS = 90;
const STANDARD_VALIDITY_DAYS = 30;
const LONG_TERM_VALIDITY_DAYS = 365;

/** Formats a Date as a local (not UTC) ISO date "yyyy-MM-dd" -- avoids the UTC-drift trap of
 *  `date.toISOString().slice(0,10)`, which can shift local midnight to the previous day. */
function toLocalIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

function addDaysLocal(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}

@Component({
  selector: 'app-prescription-wizard-page',
  imports: [
    PageHeader,
    ReactiveFormsModule,
    Stepper,
    StepList,
    Step,
    StepPanels,
    StepPanel,
    WizardStepFooter,
    DrugPicker,
    DosageEditor,
    PrescriptionItemCard,
    EPrescriptionDialog,
    FhirIntegrationNote,
    SummaryList,
    FormField,
    Message,
    Button,
    DatePicker,
    InputNumber,
    Select,
    Textarea,
    ToggleSwitch,
    DatePipe,
  ],
  templateUrl: './prescription-wizard-page.html',
  styleUrl: './prescription-wizard-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'prescription-wizard-page' },
})
export class PrescriptionWizardPage implements HasUnsavedChanges {
  readonly patientId = input.required<string>();

  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly drugService = inject(DrugService);
  private readonly prescriptionService = inject(PrescriptionService);
  private readonly staffService = inject(StaffService);
  private readonly patientContext = inject(PatientContextService);
  private readonly messageService = inject(MessageService);
  private readonly router = inject(Router);

  protected readonly patient = this.patientContext.patient;
  protected readonly prescriber = this.staffService.currentUser;

  protected readonly reimbursementOptionsForDrug = computed(() => {
    const drug = this.selectedDrug();
    if (!drug) return REIMBURSEMENT_OPTIONS;
    const allowed = new Set<string>(drug.reimbursementOptions);
    return REIMBURSEMENT_OPTIONS.filter((o) => allowed.has(o.value));
  });

  // ---- step 1: drug selection + safety check ----
  protected readonly selectedDrug = signal<Drug | null>(null);

  protected readonly safetyStep1Form = this.fb.group({
    overrideConfirmed: this.fb.control(false),
    overrideJustification: this.fb.control(''),
  });

  // Re-runs `checkSafety` through switchMap whenever the selected drug changes, so a fast
  // drug swap cannot leave stale warnings from the previous drug on screen.
  protected readonly safetyWarnings = toSignal(
    toObservable(this.selectedDrug).pipe(
      switchMap((drug) =>
        drug ? this.drugService.checkSafety(drug, this.patientId()) : of<DrugSafetyWarning[]>([]),
      ),
    ),
    { initialValue: [] as DrugSafetyWarning[] },
  );
  protected readonly safetyLoading = signal(false);

  protected readonly hasAllergyDanger = computed(() =>
    this.safetyWarnings().some((w) => w.type === 'allergy' && w.severity === 'danger'),
  );

  constructor() {
    // `safetyWarnings` recomputing (including to `[]` when the drug is cleared) means the
    // in-flight `checkSafety` call has resolved.
    effect(() => {
      this.safetyWarnings();
      this.safetyLoading.set(false);
    });
  }

  // ---- step 2: dosage ----
  protected readonly dosageForm = this.fb.group({
    dose: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01)]),
    doseUnit: this.fb.control(''),
    route: this.fb.control<string | null>(null, Validators.required),
    frequency: this.fb.control<string | null>(null, Validators.required),
    timesOfDay: this.fb.control<string[]>([]),
    asNeeded: this.fb.control(false),
    maxPerDay: this.fb.control<number | null>(null),
    instructions: this.fb.control(''),
  });

  // ---- step 3: quantity, reimbursement, therapy window ----
  protected readonly quantityForm = this.fb.group({
    durationDays: this.fb.control<number | null>(30, [
      Validators.required,
      Validators.min(1),
      Validators.max(365),
    ]),
    quantityPackages: this.fb.control<number | null>(1, [Validators.required, Validators.min(1)]),
    reimbursement: this.fb.control<string | null>(null, Validators.required),
    substitutionAllowed: this.fb.control(true),
    longTermOverride: this.fb.control(false),
  });
  private quantityDirtyByUser = false;

  // Prescription-level dates (not per-item) -- kept in their own form so a step 3 loop-back
  // for a second drug does not reset them.
  protected readonly prescriptionDatesForm = this.fb.group({
    validFrom: this.fb.control<Date>(new Date(), Validators.required),
  });

  protected readonly items = signal<PrescriptionItem[]>([]);
  protected readonly canAddMore = computed(() => this.items().length < MAX_ITEMS);

  protected readonly isLongTerm = computed(() => {
    if (this.quantityForm.controls.longTermOverride.value) return true;
    const days = this.quantityForm.controls.durationDays.value ?? 0;
    if (days > LONG_TERM_DURATION_THRESHOLD_DAYS) return true;
    return this.items().some((i) => i.dosage.durationDays > LONG_TERM_DURATION_THRESHOLD_DAYS);
  });

  protected readonly validUntil = computed(() => {
    const from = this.prescriptionDatesForm.controls.validFrom.value;
    const days = this.isLongTerm() ? LONG_TERM_VALIDITY_DAYS : STANDARD_VALIDITY_DAYS;
    return toLocalIsoDate(addDaysLocal(from, days));
  });

  // ---- step 4: issue ----
  protected readonly issuing = signal(false);
  protected readonly issuedPrescription = signal<Prescription | null>(null);
  protected readonly dialogVisible = computed(() => this.issuedPrescription() !== null);
  private submitted = false;

  protected readonly summaryItems = computed((): SummaryItem[] => [
    { label: 'Pacjent', value: this.patientSummaryLabel() },
    {
      label: 'Wystawiający',
      value: `${this.prescriber().title} ${this.prescriber().firstName} ${this.prescriber().lastName}`,
    },
    { label: 'Liczba pozycji', value: String(this.items().length) },
    {
      label: 'Ważna od',
      value: toLocalIsoDate(this.prescriptionDatesForm.controls.validFrom.value),
    },
    { label: 'Ważna do', value: this.validUntil() },
    { label: 'Leczenie przewlekłe', value: this.isLongTerm() ? 'Tak' : 'Nie' },
  ]);

  private patientSummaryLabel(): string {
    const p = this.patient();
    return p ? `${p.lastName} ${p.firstName}` : this.patientId();
  }

  // ---- step 1 handlers ----

  protected onDrugSelected(drug: Drug): void {
    this.safetyLoading.set(true);
    this.selectedDrug.set(drug);
    this.safetyStep1Form.reset({ overrideConfirmed: false, overrideJustification: '' });
    // `safetyWarnings` (built with `toSignal`/`switchMap` above) re-fires on this change;
    // its resolution is what actually clears `safetyLoading` -- see the effect below.

    // reset step 2/3 defaults for the newly selected drug
    this.dosageForm.reset({
      dose: null,
      doseUnit: drug.defaultDoseUnit,
      route: defaultRoute(drug),
      frequency: null,
      timesOfDay: [],
      asNeeded: false,
      maxPerDay: null,
      instructions: '',
    });
    this.quantityForm.patchValue({
      durationDays: 30,
      quantityPackages: 1,
      reimbursement: drug.reimbursementOptions[0] ?? null,
      substitutionAllowed: true,
      longTermOverride: false,
    });
    this.quantityDirtyByUser = false;
  }

  protected tryAdvanceStep1(activate: (v: number) => void): void {
    if (!this.selectedDrug()) {
      this.messageService.add({ severity: 'warn', summary: 'Wybierz lek, aby kontynuować.' });
      return;
    }
    if (this.safetyLoading()) {
      // Block "Dalej" while the safety check is in flight so a fast click cannot skip
      // the allergy-override gate (ADDENDUM-inspired race fix).
      return;
    }
    if (this.hasAllergyDanger()) {
      const ok = tryAdvance(this.safetyStep1Form, activate, 2);
      if (ok && !this.safetyStep1Form.value.overrideConfirmed) {
        this.safetyStep1Form.controls.overrideConfirmed.setErrors({ required: true });
      }
      if (
        this.safetyStep1Form.value.overrideConfirmed &&
        (this.safetyStep1Form.value.overrideJustification ?? '').trim().length === 0
      ) {
        this.safetyStep1Form.controls.overrideJustification.setErrors({ required: true });
        this.safetyStep1Form.markAllAsTouched();
        return;
      }
      if (!this.safetyStep1Form.value.overrideConfirmed) return;
    }
    activate(2);
  }

  // ---- step 2 handler ----

  protected tryAdvanceStep2(activate: (v: number) => void): void {
    tryAdvance(this.dosageForm, activate, 3);
  }

  // ---- step 3 handlers ----

  protected onQuantityFieldTouched(): void {
    this.quantityDirtyByUser = true;
  }

  protected recomputeSuggestedQuantity(): void {
    if (this.quantityDirtyByUser) return;
    const drug = this.selectedDrug();
    if (!drug) return;
    const dv = this.dosageForm.getRawValue();
    const daily = computeDailyDose({
      dose: dv.dose,
      doseUnit: drug.defaultDoseUnit,
      frequency: dv.frequency as never,
      asNeeded: dv.asNeeded,
      maxPerDay: dv.maxPerDay,
    });
    const days = this.quantityForm.controls.durationDays.value;
    const suggested = suggestPackageQuantity(daily, dv.dose, drug, days);
    this.quantityForm.controls.quantityPackages.setValue(suggested, { emitEvent: false });
  }

  protected addItemToList(): void {
    const drug = this.selectedDrug();
    if (!drug || !this.canAddMore()) return;

    this.dosageForm.markAllAsTouched();
    this.quantityForm.markAllAsTouched();
    if (this.dosageForm.invalid || this.quantityForm.invalid) return;

    const dv = this.dosageForm.getRawValue();
    const qv = this.quantityForm.getRawValue();

    const item: PrescriptionItem = {
      drugId: drug.id,
      drugName: drug.name,
      activeSubstance: drug.activeSubstance,
      strength: drug.strength,
      form: drug.form,
      dosage: {
        dose: dv.dose ?? 0,
        doseUnit: drug.defaultDoseUnit,
        route: dv.route as never,
        frequency: dv.frequency as never,
        timesOfDay: dv.timesOfDay.length ? (dv.timesOfDay as never) : undefined,
        durationDays: qv.durationDays ?? 30,
        asNeeded: dv.asNeeded,
        maxPerDay: dv.asNeeded ? (dv.maxPerDay ?? undefined) : undefined,
        instructions: dv.instructions || undefined,
      },
      quantityPackages: qv.quantityPackages ?? 1,
      reimbursement: (qv.reimbursement ?? 'none') as never,
      substitutionAllowed: qv.substitutionAllowed,
    };

    if (this.hasAllergyDanger() && this.safetyStep1Form.value.overrideConfirmed) {
      // `PrescriptionItem` has no field for the override justification (service gap, see
      // report) -- it is folded into the prescription-level `notes` field instead.
      const note = `${drug.name}: świadomie przepisano mimo ostrzeżenia o alergii — ${this.safetyStep1Form.value.overrideJustification}`;
      this.pendingNotes.push(note);
    }

    this.items.update((list) => [...list, item]);
    this.selectedDrug.set(null);
    this.safetyStep1Form.reset({ overrideConfirmed: false, overrideJustification: '' });
  }

  private pendingNotes: string[] = [];

  protected removeItem(index: number): void {
    this.items.update((list) => list.filter((_, i) => i !== index));
  }

  protected addAnotherDrug(activate: (v: number) => void): void {
    this.addItemToList();
    activate(1);
  }

  protected goToSummary(activate: (v: number) => void): void {
    if (this.selectedDrug() && this.dosageForm.valid && this.quantityForm.valid) {
      this.addItemToList();
    }
    if (this.items().length === 0) {
      this.messageService.add({ severity: 'warn', summary: 'Dodaj co najmniej jeden lek.' });
      return;
    }
    activate(4);
  }

  // ---- step 4 handler ----

  protected issue(): void {
    if (this.items().length === 0 || this.issuing()) return;
    this.issuing.set(true);

    const draft: PrescriptionDraft = {
      patientId: this.patientId(),
      prescriberId: this.prescriber().id,
      validFrom: toLocalIsoDate(this.prescriptionDatesForm.controls.validFrom.value),
      validUntil: this.validUntil(),
      kind: 'e_prescription',
      items: this.items(),
      notes: this.pendingNotes.length ? this.pendingNotes.join(' | ') : undefined,
    };

    this.prescriptionService.issuePrescription(draft).subscribe({
      next: (prescription) => {
        this.issuing.set(false);
        this.submitted = true;
        this.issuedPrescription.set(prescription);
        this.messageService.add({ severity: 'success', summary: 'e-Recepta wystawiona.' });
      },
      error: () => {
        this.issuing.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Nie udało się wystawić recepty.',
        });
      },
    });
  }

  protected closeDialog(): void {
    this.issuedPrescription.set(null);
    this.router.navigate(['/patients', this.patientId(), 'prescriptions']);
  }

  /** "Anuluj" in the wizard step footer -- triggers the unsaved-changes guard via normal
   *  navigation (not a bypass), same as every other wizard in the app. */
  protected closeDialogCancel(): void {
    this.router.navigate(['/patients', this.patientId(), 'prescriptions']);
  }

  hasUnsavedChanges(): boolean {
    if (this.submitted) return false;
    return (
      this.items().length > 0 ||
      this.dosageForm.dirty ||
      this.quantityForm.dirty ||
      this.selectedDrug() !== null
    );
  }
}
