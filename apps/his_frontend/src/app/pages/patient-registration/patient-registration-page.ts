import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  LOCALE_ID,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { FormBuilder } from '@angular/forms';
import { EMPTY, of, switchMap } from 'rxjs';
import { MessageService } from 'primeng/api';
import { StepperModule } from 'primeng/stepper';

import { PageHeader } from '../../components/page-header/page-header';
import { WizardStepFooter } from '../../components/wizard-step-footer/wizard-step-footer';
import { RegistrationSummary } from '../../components/registration-summary/registration-summary';

import { PatientService } from '../../services/patient.service';
import { PatientContextService } from '../../services/patient-context.service';
import { WardService } from '../../services/ward.service';
import { StaffService } from '../../services/staff.service';

import { parsePesel } from '../../validators/pesel.validator';
import { rawValueSignal } from '../../utils/form-signals';
import { tryAdvance } from '../../utils/wizard';
import { ageFromBirthDate } from '../../utils/date-utils';

import type { HasUnsavedChanges } from '../../guards/unsaved-changes.guard';
import type { Patient, StaffMember, Ward } from '../../models';

import { AdmissionStep } from './admission-step/admission-step';
import { IdentityStep } from './identity-step/identity-step';
import { InsuranceStep } from './insurance-step/insurance-step';
import { PersonalStep } from './personal-step/personal-step';
import {
  applyAdmissionType,
  applyNoPesel,
  createAdmissionForm,
  createIdentityForm,
  createInsuranceForm,
  createPersonalForm,
  fromIsoDate,
  prefillForms,
  toIsoDate,
} from './patient-registration.forms';
import {
  buildAdmission,
  buildPatientDraft,
  buildSummarySections,
} from './patient-registration.mappers';

@Component({
  selector: 'app-patient-registration-page',
  imports: [
    PageHeader,
    WizardStepFooter,
    RegistrationSummary,
    StepperModule,
    IdentityStep,
    PersonalStep,
    InsuranceStep,
    AdmissionStep,
  ],
  templateUrl: './patient-registration-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'patient-registration-page' },
})
export class PatientRegistrationPage implements HasUnsavedChanges {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly router = inject(Router);
  private readonly patientService = inject(PatientService);
  private readonly ctx = inject(PatientContextService);
  private readonly wardService = inject(WardService);
  private readonly staffService = inject(StaffService);
  private readonly messageService = inject(MessageService);
  private readonly locale = inject(LOCALE_ID);

  readonly mode = input<'create' | 'edit'>('create');
  readonly patientId = input<string>();

  protected readonly activeStep = signal(1);
  protected readonly submitting = signal(false);
  protected readonly submitted = signal(false);
  protected readonly loadingPatient = signal(false);

  protected readonly duplicatePatient = signal<{ id: string; label: string } | null>(null);
  protected readonly wards = signal<Ward[]>([]);
  protected readonly doctors = signal<StaffMember[]>([]);

  protected readonly step1 = createIdentityForm(this.fb);
  protected readonly step2 = createPersonalForm(this.fb);
  protected readonly step3 = createInsuranceForm(this.fb);
  protected readonly step4 = createAdmissionForm(this.fb);

  private readonly step1Value = rawValueSignal(this.step1);
  private readonly step2Value = rawValueSignal(this.step2);
  private readonly step3Value = rawValueSignal(this.step3);
  private readonly step4Value = rawValueSignal(this.step4);

  protected readonly isAmbulatoryOnly = () => this.step4Value().admissionType === 'outpatient';

  protected readonly age = () => {
    const bd = this.step1Value().birthDate;
    return bd ? ageFromBirthDate(toIsoDate(bd)) : null;
  };

  protected readonly guardianRequired = () => {
    const a = this.age();
    return a !== null && a < 18;
  };

  private originalPatient: Patient | null = null;

  constructor() {
    this.wardService.getWards().subscribe((w) => this.wards.set(w));
    this.staffService.getStaff('doctor').subscribe((d) => this.doctors.set(d));

    // PESEL <-> manual-birth-date branch toggling.
    this.step1.controls.noPesel.valueChanges.subscribe((noPesel) => {
      applyNoPesel(this.step1, noPesel);
      if (noPesel) this.duplicatePatient.set(null);
    });

    this.step1.controls.pesel.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((value) => this.onPeselChange(value));

    this.step4.controls.admissionType.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((type) => applyAdmissionType(this.step4, type));
    applyAdmissionType(this.step4, this.step4.controls.admissionType.value);

    // `mode`/`patientId` are route-bound signal inputs -- still default at construction
    // time, so gate on the observable stream instead of reading `this.mode()` here.
    toObservable(this.patientId)
      .pipe(
        switchMap((id) => {
          if (this.mode() !== 'edit' || !id) return EMPTY;
          this.loadingPatient.set(true);
          return this.patientService.getPatientById(id);
        }),
        takeUntilDestroyed(),
      )
      .subscribe((patient) => {
        this.originalPatient = patient;
        prefillForms(
          { step1: this.step1, step2: this.step2, step3: this.step3, step4: this.step4 },
          patient,
        );
        this.loadingPatient.set(false);
      });
  }

  private onPeselChange(value: string): void {
    this.duplicatePatient.set(null);
    if (!value || this.step1.controls.pesel.hasError('pesel')) return;

    const parsed = parsePesel(value);
    if (parsed) {
      this.step1.controls.birthDate.setValue(fromIsoDate(parsed.birthDate));
      this.step1.controls.gender.setValue(parsed.gender);
    }

    this.patientService.findByPesel(value).subscribe((match) => {
      if (match && match.id !== this.patientId()) {
        this.duplicatePatient.set({
          id: match.id,
          label: `${match.lastName} ${match.firstName}`,
        });
      }
    });
  }

  protected verifyEwus(): void {
    this.step3.controls.insuranceStatus.setValue('active');
    this.step3.controls.ewusVerifiedAt.setValue(new Date().toISOString());
    // setValue() alone doesn't mark the group dirty -- without this, a user who only
    // clicks "Weryfikuj eWUŚ" and navigates away gets no unsaved-changes prompt.
    this.step3.markAsDirty();
  }

  protected tryAdvanceStep1(activate: (v: number) => void): void {
    if (this.duplicatePatient()) return;
    tryAdvance(this.step1, activate, 2);
  }

  protected tryAdvanceStep2(activate: (v: number) => void): void {
    tryAdvance(this.step2, activate, 3);
  }

  protected tryAdvanceStep3(activate: (v: number) => void): void {
    if (this.guardianRequired() && !this.step3.controls.contactFullName.value) {
      this.step3.controls.contactFullName.setErrors({ required: true });
      this.step3.markAllAsTouched();
      return;
    }
    tryAdvance(this.step3, activate, 4);
  }

  protected tryAdvanceStep4(activate: (v: number) => void): void {
    if (this.mode() === 'edit') {
      // Step 4 shows a read-only "Zmiany" review in edit mode -- step4's admission
      // fields are not rendered/edited here, so skip validating them entirely.
      activate(5);
      return;
    }
    tryAdvance(this.step4, activate, 5);
  }

  protected readonly summarySections = () =>
    buildSummarySections({
      s1: this.step1Value(),
      s2: this.step2Value(),
      s3: this.step3Value(),
      s4: this.step4Value(),
      mode: this.mode(),
      locale: this.locale,
      wardName: (id) => this.wardService.nameOf(id),
      staffName: (id) => this.staffService.nameOf(id),
    });

  protected submit(): void {
    if (this.duplicatePatient()) return;
    this.submitting.set(true);

    const s4 = this.step4.getRawValue();
    const draft = buildPatientDraft({
      s1: this.step1.getRawValue(),
      s2: this.step2.getRawValue(),
      s3: this.step3.getRawValue(),
      s4,
      mode: this.mode(),
      original: this.originalPatient,
    });

    if (this.mode() === 'edit') {
      const id = this.patientId();
      if (!id) return;
      this.patientService.updatePatient(id, draft).subscribe({
        next: () => {
          this.submitted.set(true);
          this.submitting.set(false);
          this.ctx.refresh().subscribe();
          this.messageService.add({ severity: 'success', summary: 'Dane pacjenta zaktualizowane' });
          this.router.navigate(['/patients', id, 'overview']);
        },
        error: () => this.fail('Nie udało się zapisać zmian'),
      });
      return;
    }

    const needsAdmission = s4.admissionType !== 'outpatient';

    this.patientService
      .createPatient(draft)
      .pipe(
        switchMap((patient) =>
          needsAdmission
            ? this.patientService.admitPatient(patient.id, buildAdmission(s4))
            : of(patient),
        ),
      )
      .subscribe({
        next: (patient) => {
          this.submitted.set(true);
          this.submitting.set(false);
          this.ctx.setPatient(patient);
          this.messageService.add({
            severity: 'success',
            summary: `Pacjent zarejestrowany. Nadano nr historii choroby: ${patient.mrn}`,
          });
          this.router.navigate(['/patients', patient.id, 'overview']);
        },
        error: () => this.fail('Nie udało się zarejestrować pacjenta'),
      });
  }

  private fail(summary: string): void {
    this.submitting.set(false);
    this.messageService.add({ severity: 'error', summary });
  }

  protected cancel(): void {
    if (this.mode() === 'edit' && this.patientId()) {
      this.router.navigate(['/patients', this.patientId(), 'overview']);
    } else {
      this.router.navigate(['/patients']);
    }
  }

  hasUnsavedChanges(): boolean {
    if (this.submitted()) return false;
    return this.step1.dirty || this.step2.dirty || this.step3.dirty || this.step4.dirty;
  }
}
