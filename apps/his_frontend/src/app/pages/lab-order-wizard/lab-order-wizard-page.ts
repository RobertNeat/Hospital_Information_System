import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';
import { rxResource } from '@angular/core/rxjs-interop';
import {
  FormBuilder,
  FormControl,
  FormRecord,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { forkJoin } from 'rxjs';
import { MessageService } from 'primeng/api';
import { Step, StepList, StepPanel, StepPanels, Stepper } from 'primeng/stepper';
import { Select } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { DatePicker } from 'primeng/datepicker';
import { Textarea } from 'primeng/textarea';
import { Message } from 'primeng/message';
import { CatalogPicker, type CatalogPanel } from '../../components/catalog-picker/catalog-picker';
import { FhirIntegrationNote } from '../../components/fhir-integration-note/fhir-integration-note';
import { FormField } from '../../components/form-field/form-field';
import { PageHeader } from '../../components/page-header/page-header';
import { SummaryList, type SummaryItem } from '../../components/summary-list/summary-list';
import { WizardStepFooter } from '../../components/wizard-step-footer/wizard-step-footer';
import { SPECIMEN_LABELS, URGENCY_OPTIONS } from '../../constants/labels';
import type { HasUnsavedChanges } from '../../guards/unsaved-changes.guard';
import type { Coding, LabTest, OrderUrgency, SpecimenType } from '../../models';
import { PatientContextService } from '../../services/patient-context.service';
import { EhrService } from '../../services/ehr.service';
import { LabOrderService } from '../../services/lab-order.service';
import { StaffService } from '../../services/staff.service';
import { tryAdvance } from '../../utils/wizard';

interface DiagnosisOption {
  label: string;
  value: string;
  coding: Coding;
}

@Component({
  selector: 'app-lab-order-wizard-page',
  imports: [
    PageHeader,
    Stepper,
    StepList,
    Step,
    StepPanels,
    StepPanel,
    WizardStepFooter,
    ReactiveFormsModule,
    CatalogPicker,
    Select,
    SelectButton,
    ToggleSwitch,
    DatePicker,
    Textarea,
    Message,
    FormField,
    SummaryList,
    FhirIntegrationNote,
  ],
  templateUrl: './lab-order-wizard-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LabOrderWizardPage implements HasUnsavedChanges {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly ehrService = inject(EhrService);
  private readonly labOrderService = inject(LabOrderService);
  private readonly staffService = inject(StaffService);
  private readonly ctx = inject(PatientContextService);
  private readonly router = inject(Router);
  private readonly toast = inject(MessageService);

  readonly patientId = input.required<string>();

  protected readonly submitted = signal(false);
  protected readonly submitting = signal(false);

  protected readonly urgencyOptions = URGENCY_OPTIONS;
  protected readonly specimenLabels = SPECIMEN_LABELS;

  protected specimenLabel(value: SpecimenType): string {
    return SPECIMEN_LABELS[value];
  }

  private readonly catalogResource = rxResource({
    stream: () =>
      forkJoin({
        catalog: this.labOrderService.getCatalog(),
        panels: this.labOrderService.getPanels(),
      }),
  });

  protected readonly catalog = computed<LabTest[]>(
    () => this.catalogResource.value()?.catalog ?? [],
  );
  protected readonly panels = computed<CatalogPanel[]>(
    () => this.catalogResource.value()?.panels ?? [],
  );

  private readonly diagnosesResource = rxResource({
    params: () => this.patientId(),
    stream: ({ params: pid }) =>
      forkJoin({
        diagnoses: this.ehrService.getDiagnoses(pid),
        icd10: this.ehrService.getIcd10Dictionary(),
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

  // ---- Step 1: Wybór badań ----
  protected readonly selectedCodes = signal<string[]>([]);

  protected readonly step1Form = this.fb.group({
    selectedCodes: this.fb.control<string[]>([], { validators: [Validators.required] }),
  });

  protected onSelectedCodesChange(codes: string[]): void {
    this.selectedCodes.set(codes);
    this.step1Form.controls.selectedCodes.setValue(codes);
    this.step1Form.controls.selectedCodes.markAsDirty();
    this.syncSpecimenControls(codes);
  }

  protected readonly selectedTests = computed<LabTest[]>(() => {
    const codes = new Set(this.selectedCodes());
    return this.catalog().filter((t) => codes.has(t.code));
  });

  // ---- Step 2: Materiał i pilność ----
  protected readonly specimenForm = new FormRecord<FormControl<SpecimenType>>({});

  private syncSpecimenControls(codes: string[]): void {
    const existing = this.specimenForm.controls;
    for (const key of Object.keys(existing)) {
      if (!codes.includes(key)) this.specimenForm.removeControl(key);
    }
    for (const code of codes) {
      if (existing[code]) continue;
      const test = this.catalog().find((t) => t.code === code);
      const control = new FormControl<SpecimenType>(test?.defaultSpecimen ?? 'blood', {
        nonNullable: true,
      });
      this.specimenForm.addControl(code, control);
    }
  }

  protected readonly anyFastingRequired = computed(() =>
    this.selectedTests().some((t) => t.fastingRequired),
  );

  protected readonly step2Form = this.fb.group({
    urgency: this.fb.control<OrderUrgency>('routine', { validators: [Validators.required] }),
    fasting: this.fb.control(false),
    plannedCollectionAt: this.fb.control<Date>(new Date(), { validators: [Validators.required] }),
  });

  protected readonly minCollectionDate = new Date();

  constructor() {
    this.step2Form.controls.urgency.valueChanges.subscribe((urgency) => {
      if (urgency === 'stat') {
        this.step2Form.controls.plannedCollectionAt.setValue(new Date());
      }
    });
  }

  protected onFastingLockCheck(): void {
    if (this.anyFastingRequired() && !this.step2Form.controls.fasting.value) {
      this.step2Form.controls.fasting.setValue(true);
    }
  }

  protected advanceFromStep1(activate: (value: number) => void): void {
    tryAdvance(this.step1Form, activate, 2);
  }

  protected advanceFromStep2(activate: (value: number) => void): void {
    this.onFastingLockCheck();
    tryAdvance(this.step2Form, activate, 3);
  }

  protected advanceFromStep3(activate: (value: number) => void): void {
    tryAdvance(this.step3Form, activate, 4);
  }

  // ---- Step 3: Informacje kliniczne ----
  protected readonly step3Form = this.fb.group({
    diagnosisCode: this.fb.control<string | null>(null),
    clinicalInfo: this.fb.control('', {
      validators: [Validators.required, Validators.minLength(10)],
    }),
    notes: this.fb.control(''),
  });

  // ---- Step 4: Summary ----
  protected readonly summaryItems = computed<SummaryItem[]>(() => {
    const tests = this.selectedTests();
    const specimens = tests
      .map(
        (t) =>
          `${t.name}: ${SPECIMEN_LABELS[this.specimenForm.controls[t.code]?.value ?? t.defaultSpecimen]}`,
      )
      .join('; ');
    const urgencyLabel =
      URGENCY_OPTIONS.find((o) => o.value === this.step2Form.controls.urgency.value)?.label ?? '';
    const diagCode = this.step3Form.controls.diagnosisCode.value;
    const diag = this.diagnosisOptions().find((o) => o.value === diagCode);
    return [
      { label: 'Badania', value: tests.map((t) => t.name).join(', ') || '—' },
      { label: 'Materiał', value: specimens || '—' },
      { label: 'Pilność', value: urgencyLabel },
      {
        label: 'Na czczo',
        value: this.step2Form.controls.fasting.value ? 'Tak' : 'Nie',
      },
      {
        label: 'Planowany termin pobrania',
        value: this.formatDate(this.step2Form.controls.plannedCollectionAt.value),
      },
      { label: 'Rozpoznanie', value: diag?.label ?? '—' },
      { label: 'Informacje kliniczne', value: this.step3Form.controls.clinicalInfo.value },
      { label: 'Uwagi dla laboratorium', value: this.step3Form.controls.notes.value || '—' },
    ];
  });

  private formatDate(d: Date): string {
    return d.toLocaleString('pl-PL', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  hasUnsavedChanges(): boolean {
    if (this.submitted()) return false;
    return (
      this.step1Form.dirty ||
      this.specimenForm.dirty ||
      this.step2Form.dirty ||
      this.step3Form.dirty
    );
  }

  protected submit(): void {
    this.step3Form.markAllAsTouched();
    this.step3Form.updateValueAndValidity();
    if (this.step3Form.invalid || !this.selectedTests().length) {
      this.toast.add({
        severity: 'warn',
        summary: 'Uzupełnij wymagane pola',
        detail: 'Sprawdź wszystkie kroki formularza przed wysłaniem zlecenia.',
      });
      return;
    }

    const diagCode = this.step3Form.controls.diagnosisCode.value;
    const diag = this.diagnosisOptions().find((o) => o.value === diagCode);

    this.submitting.set(true);
    this.labOrderService
      .createOrder({
        patientId: this.patientId(),
        orderedById: this.staffService.currentUser().id,
        items: this.selectedTests().map((t) => ({
          testCode: t.code,
          testName: t.name,
          specimenType: this.specimenForm.controls[t.code]?.value ?? t.defaultSpecimen,
        })),
        urgency: this.step2Form.controls.urgency.value,
        fasting: this.step2Form.controls.fasting.value,
        plannedCollectionAt: this.step2Form.controls.plannedCollectionAt.value.toISOString(),
        diagnosisCode: diag?.coding,
        clinicalInfo: this.step3Form.controls.clinicalInfo.value,
        notes: this.step3Form.controls.notes.value || undefined,
      })
      .subscribe({
        next: () => {
          this.submitting.set(false);
          this.submitted.set(true);
          const isCito = this.step2Form.controls.urgency.value === 'stat';
          this.toast.add({
            severity: 'success',
            summary: isCito ? 'Zlecenie CITO wysłane' : 'Zlecenie wysłane',
            detail: 'Zlecenie laboratoryjne zostało zapisane.',
          });
          void this.router.navigate(['/patients', this.patientId(), 'orders'], {
            queryParams: { type: 'lab' },
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
