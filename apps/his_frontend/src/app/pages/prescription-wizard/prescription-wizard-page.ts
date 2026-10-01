import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { Message } from 'primeng/message';
import { StepList, StepPanel, StepPanels, Step, Stepper } from 'primeng/stepper';
import { Textarea } from 'primeng/textarea';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { DosageEditor } from '../../components/dosage-editor/dosage-editor';
import { DrugPicker } from '../../components/drug-picker/drug-picker';
import { EPrescriptionDialog } from '../../components/e-prescription-dialog/e-prescription-dialog';
import { FhirIntegrationNote } from '../../components/fhir-integration-note/fhir-integration-note';
import { PrescriptionQuantityFields } from '../../components/prescription-quantity-fields/prescription-quantity-fields';
import { FormField } from '../../components/form-field/form-field';
import { PageHeader } from '../../components/page-header/page-header';
import { PrescriptionItemCard } from '../../components/prescription-item-card/prescription-item-card';
import { SummaryList, type SummaryItem } from '../../components/summary-list/summary-list';
import { WizardStepFooter } from '../../components/wizard-step-footer/wizard-step-footer';
import { REIMBURSEMENT_OPTIONS } from '../../constants/labels';
import type { HasUnsavedChanges } from '../../guards/unsaved-changes.guard';
import type { Drug, Prescription, PrescriptionItem } from '../../models';
import { PatientContextService } from '../../services/patient-context.service';
import { PrescriptionService } from '../../services/prescription.service';
import { toApiError } from '../../utils/api-error';
import { StaffService } from '../../services/staff.service';
import { tryAdvance } from '../../utils/wizard';
import { createDrugSafetyState } from './prescription-wizard.safety';
import {
  LONG_TERM_DURATION_THRESHOLD_DAYS,
  MAX_ITEMS,
  buildPrescriptionDraft,
  buildPrescriptionItem,
  buildPrescriptionSummary,
  computeValidUntil,
  createDosageForm,
  dosageDefaultsFor,
  quantityDefaultsFor,
  suggestQuantity,
  createQuantityForm,
} from './prescription-wizard.helpers';

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
    PrescriptionQuantityFields,
    Message,
    Button,
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

  protected readonly items = signal<PrescriptionItem[]>([]);

  private readonly safety = createDrugSafetyState(this.selectedDrug, this.patientId, this.items);
  protected readonly safetyStep1Form = this.safety.form;
  protected readonly safetyWarnings = this.safety.warnings;
  protected readonly safetyLoading = this.safety.loading;
  protected readonly safetyCheckFailed = this.safety.checkFailed;
  protected readonly hasAllergyDanger = this.safety.hasAllergyDanger;

  // ---- step 2: dosage ----
  protected readonly dosageForm = createDosageForm(this.fb);

  // ---- step 3: quantity, reimbursement, therapy window ----
  protected readonly quantityForm = createQuantityForm(this.fb);
  private quantityDirtyByUser = false;
  private pendingNotes: string[] = [];

  // Prescription-level dates (not per-item) -- kept in their own form so a step 3 loop-back
  // for a second drug does not reset them.
  protected readonly prescriptionDatesForm = this.fb.group({
    validFrom: this.fb.control<Date>(new Date(), Validators.required),
  });

  protected readonly canAddMore = computed(() => this.items().length < MAX_ITEMS);

  protected readonly isLongTerm = computed(() => {
    if (this.quantityForm.controls.longTermOverride.value) return true;
    const days = this.quantityForm.controls.durationDays.value ?? 0;
    if (days > LONG_TERM_DURATION_THRESHOLD_DAYS) return true;
    return this.items().some((i) => i.dosage.durationDays > LONG_TERM_DURATION_THRESHOLD_DAYS);
  });

  protected readonly validUntil = computed(() => {
    return computeValidUntil(
      this.prescriptionDatesForm.controls.validFrom.value,
      this.isLongTerm(),
    );
  });

  // ---- step 4: issue ----
  protected readonly issuing = signal(false);
  protected readonly issuedPrescription = signal<Prescription | null>(null);
  protected readonly dialogVisible = computed(() => this.issuedPrescription() !== null);
  private submitted = false;

  protected readonly summaryItems = computed((): SummaryItem[] =>
    buildPrescriptionSummary({
      patient: this.patient(),
      patientId: this.patientId(),
      prescriber: this.prescriber(),
      itemCount: this.items().length,
      validFrom: this.prescriptionDatesForm.controls.validFrom.value,
      validUntil: this.validUntil(),
      longTerm: this.isLongTerm(),
    }),
  );

  // ---- step 1 handlers ----

  protected onDrugSelected(drug: Drug): void {
    this.safetyLoading.set(true);
    this.selectedDrug.set(drug);
    this.safety.resetOverride();
    // `safetyWarnings` (built with `toSignal`/`switchMap` above) re-fires on this change;
    // its resolution is what actually clears `safetyLoading` -- see the effect below.

    // reset step 2/3 defaults for the newly selected drug
    this.dosageForm.reset(dosageDefaultsFor(drug));
    this.quantityForm.patchValue(quantityDefaultsFor(drug));
    this.quantityDirtyByUser = false;
  }

  protected tryAdvanceStep1(activate: (v: number) => void): void {
    if (!this.selectedDrug()) {
      this.messageService.add({ severity: 'warn', summary: 'Wybierz lek, aby kontynuować.' });
      return;
    }
    this.safety.advance(activate);
  }

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
    this.quantityForm.controls.quantityPackages.setValue(
      suggestQuantity(
        drug,
        this.dosageForm.getRawValue(),
        this.quantityForm.controls.durationDays.value,
      ),
      { emitEvent: false },
    );
  }

  protected addItemToList(): void {
    const drug = this.selectedDrug();
    if (!drug || !this.canAddMore()) return;

    this.dosageForm.markAllAsTouched();
    this.quantityForm.markAllAsTouched();
    if (this.dosageForm.invalid || this.quantityForm.invalid) return;

    const dv = this.dosageForm.getRawValue();
    const qv = this.quantityForm.getRawValue();
    const item = buildPrescriptionItem(drug, dv, qv);

    if (this.hasAllergyDanger() && this.safetyStep1Form.value.overrideConfirmed) {
      // `PrescriptionItem` has no field for the override justification (service gap, see
      // report) -- it is folded into the prescription-level `notes` field instead.
      this.pendingNotes.push(
        `${drug.name}: świadomie przepisano mimo ostrzeżenia o alergii — ${this.safetyStep1Form.value.overrideJustification}`,
      );
    }

    this.items.update((list) => [...list, item]);
    this.selectedDrug.set(null);
    this.safety.resetOverride();
  }

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

  protected issue(): void {
    if (this.items().length === 0 || this.issuing()) return;
    this.issuing.set(true);

    const draft = buildPrescriptionDraft({
      patientId: this.patientId(),
      prescriberId: this.prescriber().id,
      validFrom: this.prescriptionDatesForm.controls.validFrom.value,
      validUntil: this.validUntil(),
      items: this.items(),
      notes: this.pendingNotes,
    });

    this.prescriptionService.issuePrescription(draft).subscribe({
      next: (prescription) => {
        this.issuing.set(false);
        this.submitted = true;
        this.issuedPrescription.set(prescription);
        this.messageService.add({ severity: 'success', summary: 'e-Recepta wystawiona.' });
      },
      error: (err: unknown) => {
        this.issuing.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Nie udało się wystawić recepty.',
          detail: toApiError(err).problem.detail,
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
