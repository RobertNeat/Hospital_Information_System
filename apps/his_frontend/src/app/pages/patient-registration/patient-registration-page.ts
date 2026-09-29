import {
  ChangeDetectionStrategy,
  Component,
  inject,
  input,
  LOCALE_ID,
  signal,
} from '@angular/core';
import { DatePipe, formatDate } from '@angular/common';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { EMPTY, map, of, startWith, switchMap } from 'rxjs';
import { MessageService } from 'primeng/api';
import { Button } from 'primeng/button';
import { StepperModule } from 'primeng/stepper';
import { Select } from 'primeng/select';
import { DatePicker } from 'primeng/datepicker';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { InputMask } from 'primeng/inputmask';
import { InputText } from 'primeng/inputtext';
import { SelectButton } from 'primeng/selectbutton';
import { Textarea } from 'primeng/textarea';
import { Message } from 'primeng/message';

import { PageHeader } from '../../components/page-header/page-header';
import { FormField } from '../../components/form-field/form-field';
import { WizardStepFooter } from '../../components/wizard-step-footer/wizard-step-footer';
import {
  RegistrationSummary,
  type RegistrationSummarySection,
} from '../../components/registration-summary/registration-summary';

import { PatientService } from '../../services/patient.service';
import { PatientContextService } from '../../services/patient-context.service';
import { WardService } from '../../services/ward.service';
import { StaffService } from '../../services/staff.service';

import { peselValidator, parsePesel } from '../../validators/pesel.validator';
import { postalCodeValidator } from '../../validators/postal-code.validator';
import { phoneValidator } from '../../validators/phone.validator';
import { tryAdvance } from '../../utils/wizard';
import { ageFromBirthDate } from '../../utils/date-utils';

import {
  ADMISSION_TYPE_OPTIONS,
  BLOOD_TYPE_OPTIONS,
  GENDER_OPTIONS,
  IDENTITY_DOCUMENT_TYPE_OPTIONS,
  INSURANCE_PAYER_OPTIONS,
  INSURANCE_STATUS_LABELS,
  NFZ_BRANCH_OPTIONS,
  NO_PESEL_REASON_OPTIONS,
  TRIAGE_OPTIONS,
  ADMISSION_TYPE_LABELS,
  GENDER_LABELS,
  IDENTITY_DOCUMENT_TYPE_LABELS,
  NO_PESEL_REASON_LABELS,
  TRIAGE_LABELS,
} from '../../constants/labels';

import type { HasUnsavedChanges } from '../../guards/unsaved-changes.guard';
import type {
  Admission,
  AdmissionStatus,
  Gender,
  Patient,
  PatientDraft,
  StaffMember,
  TriageLevel,
  Ward,
} from '../../models';

type AdmissionType = 'planned' | 'emergency' | 'transfer' | 'outpatient';

/** Formats a JS `Date` as a local `YYYY-MM-DD` ISO date string (never UTC-shifted). */
function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function fromIsoDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

@Component({
  selector: 'app-patient-registration-page',
  imports: [
    PageHeader,
    FormField,
    WizardStepFooter,
    RegistrationSummary,
    ReactiveFormsModule,
    Button,
    StepperModule,
    Select,
    DatePicker,
    ToggleSwitch,
    InputMask,
    InputText,
    SelectButton,
    Textarea,
    Message,
    RouterLink,
    DatePipe,
  ],
  templateUrl: './patient-registration-page.html',
  styleUrl: './patient-registration-page.css',
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

  protected readonly genderOptions = GENDER_OPTIONS;
  protected readonly noPeselReasonOptions = NO_PESEL_REASON_OPTIONS;
  protected readonly identityDocumentTypeOptions = IDENTITY_DOCUMENT_TYPE_OPTIONS;
  protected readonly insurancePayerOptions = INSURANCE_PAYER_OPTIONS;
  protected readonly nfzBranchOptions = NFZ_BRANCH_OPTIONS;
  protected readonly bloodTypeOptions = BLOOD_TYPE_OPTIONS;
  protected readonly admissionTypeOptions = ADMISSION_TYPE_OPTIONS;
  protected readonly triageOptions = TRIAGE_OPTIONS;

  // ---- Step 1: Identyfikacja ----
  protected readonly step1 = this.fb.group({
    noPesel: this.fb.control(false),
    pesel: this.fb.control('', [Validators.required, peselValidator()]),
    birthDate: this.fb.control<Date | null>(null, Validators.required),
    gender: this.fb.control<Gender>('unknown'),
    noPeselReason: this.fb.control<'foreigner' | 'newborn' | 'unknown_identity' | null>(null),
    documentType: this.fb.control<'id_card' | 'passport' | 'other' | null>(null),
    documentNumber: this.fb.control(''),
  });

  // ---- Step 2: Dane osobowe i kontaktowe ----
  protected readonly step2 = this.fb.group({
    firstName: this.fb.control('', Validators.required),
    secondName: this.fb.control(''),
    lastName: this.fb.control('', Validators.required),
    phone: this.fb.control('', [phoneValidator()]),
    email: this.fb.control('', [Validators.email]),
    street: this.fb.control('', Validators.required),
    buildingNumber: this.fb.control('', Validators.required),
    apartmentNumber: this.fb.control(''),
    postalCode: this.fb.control('', [Validators.required, postalCodeValidator()]),
    city: this.fb.control('', Validators.required),
    country: this.fb.control('Polska', Validators.required),
  });

  // ---- Step 3: Ubezpieczenie i osoba kontaktowa ----
  protected readonly step3 = this.fb.group({
    insuranceStatus: this.fb.control<'active' | 'inactive' | 'unknown'>('unknown'),
    insurancePayer: this.fb.control<'NFZ' | 'private' | 'none'>('NFZ'),
    nfzBranch: this.fb.control(''),
    ewusVerifiedAt: this.fb.control<string | null>(null),
    contactFullName: this.fb.control(''),
    contactRelation: this.fb.control(''),
    contactPhone: this.fb.control(''),
    isLegalGuardian: this.fb.control(false),
    bloodType: this.fb.control<string | null>(null),
  });

  // ---- Step 4: Przyjęcie ----
  protected readonly step4 = this.fb.group({
    admissionType: this.fb.control<AdmissionType>('planned'),
    wardId: this.fb.control(''),
    room: this.fb.control(''),
    bed: this.fb.control(''),
    attendingPhysicianId: this.fb.control(''),
    triageLevel: this.fb.control<TriageLevel | null>(null),
    reason: this.fb.control(''),
    referralNumber: this.fb.control(''),
    admittedAt: this.fb.control<Date>(new Date()),
  });

  private readonly step1Value = toSignal(
    this.step1.valueChanges.pipe(
      startWith(null),
      map(() => this.step1.getRawValue()),
    ),
    { initialValue: this.step1.getRawValue() },
  );

  private readonly step4Value = toSignal(
    this.step4.valueChanges.pipe(
      startWith(null),
      map(() => this.step4.getRawValue()),
    ),
    { initialValue: this.step4.getRawValue() },
  );

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
      if (noPesel) {
        this.step1.controls.pesel.disable({ emitEvent: false });
        this.step1.controls.noPeselReason.setValidators(Validators.required);
        this.duplicatePatient.set(null);
      } else {
        this.step1.controls.pesel.enable({ emitEvent: false });
        this.step1.controls.noPeselReason.clearValidators();
      }
      // `birthDate` stays required in both branches (auto-filled from PESEL, or entered manually).
      this.step1.controls.noPeselReason.updateValueAndValidity({ emitEvent: false });
    });

    this.step1.controls.pesel.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((value) => this.onPeselChange(value));

    this.step4.controls.admissionType.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((type) => this.onAdmissionTypeChange(type));
    this.onAdmissionTypeChange(this.step4.controls.admissionType.value);

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
        this.prefill(patient);
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

  private onAdmissionTypeChange(type: AdmissionType): void {
    const controls = this.step4.controls;
    if (type === 'outpatient') {
      controls.wardId.disable({ emitEvent: false });
      controls.room.disable({ emitEvent: false });
      controls.bed.disable({ emitEvent: false });
      controls.attendingPhysicianId.disable({ emitEvent: false });
      controls.triageLevel.disable({ emitEvent: false });
      controls.reason.disable({ emitEvent: false });
      controls.referralNumber.disable({ emitEvent: false });
      controls.admittedAt.disable({ emitEvent: false });
      controls.wardId.clearValidators();
      controls.attendingPhysicianId.clearValidators();
      controls.reason.clearValidators();
      controls.triageLevel.clearValidators();
    } else {
      controls.wardId.enable({ emitEvent: false });
      controls.room.enable({ emitEvent: false });
      controls.bed.enable({ emitEvent: false });
      controls.attendingPhysicianId.enable({ emitEvent: false });
      controls.triageLevel.enable({ emitEvent: false });
      controls.reason.enable({ emitEvent: false });
      controls.referralNumber.enable({ emitEvent: false });
      controls.admittedAt.enable({ emitEvent: false });
      controls.wardId.setValidators(Validators.required);
      controls.attendingPhysicianId.setValidators(Validators.required);
      controls.reason.setValidators(Validators.required);
      controls.triageLevel.setValidators(type === 'emergency' ? Validators.required : null);
    }
    controls.wardId.updateValueAndValidity({ emitEvent: false });
    controls.attendingPhysicianId.updateValueAndValidity({ emitEvent: false });
    controls.reason.updateValueAndValidity({ emitEvent: false });
    controls.triageLevel.updateValueAndValidity({ emitEvent: false });
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

  private readonly step2Value = toSignal(
    this.step2.valueChanges.pipe(
      startWith(null),
      map(() => this.step2.getRawValue()),
    ),
    { initialValue: this.step2.getRawValue() },
  );

  private readonly step3Value = toSignal(
    this.step3.valueChanges.pipe(
      startWith(null),
      map(() => this.step3.getRawValue()),
    ),
    { initialValue: this.step3.getRawValue() },
  );

  private formatBirthDate(d: Date): string {
    return formatDate(toIsoDate(d), 'dd.MM.yyyy', this.locale);
  }

  private formatDateTime(iso: string): string {
    return formatDate(iso, 'dd.MM.yyyy HH:mm', this.locale);
  }

  protected readonly summarySections = (): RegistrationSummarySection[] => {
    const s1 = this.step1Value();
    const s2 = this.step2Value();
    const s3 = this.step3Value();
    const s4 = this.step4Value();

    const sections: RegistrationSummarySection[] = [
      {
        title: 'Identyfikacja',
        icon: 'pi pi-id-card',
        items: s1.noPesel
          ? [
              {
                label: 'Powód braku PESEL',
                value: s1.noPeselReason ? NO_PESEL_REASON_LABELS[s1.noPeselReason] : null,
              },
              {
                label: 'Dokument',
                value: s1.documentType ? IDENTITY_DOCUMENT_TYPE_LABELS[s1.documentType] : null,
              },
              { label: 'Nr dokumentu', value: s1.documentNumber || null },
              {
                label: 'Data urodzenia',
                value: s1.birthDate ? this.formatBirthDate(s1.birthDate) : null,
              },
              { label: 'Płeć', value: GENDER_LABELS[s1.gender] },
            ]
          : [
              { label: 'PESEL', value: s1.pesel || null },
              {
                label: 'Data urodzenia',
                value: s1.birthDate ? this.formatBirthDate(s1.birthDate) : null,
              },
              { label: 'Płeć', value: GENDER_LABELS[s1.gender] },
            ],
      },
      {
        title: 'Dane osobowe i kontaktowe',
        icon: 'pi pi-users',
        items: [
          {
            label: 'Imię i nazwisko',
            value:
              `${s2.firstName} ${s2.secondName ? s2.secondName + ' ' : ''}${s2.lastName}`.trim(),
          },
          { label: 'Telefon', value: s2.phone || null },
          { label: 'E-mail', value: s2.email || null },
          {
            label: 'Adres',
            value: `${s2.street} ${s2.buildingNumber}${s2.apartmentNumber ? '/' + s2.apartmentNumber : ''}, ${s2.postalCode} ${s2.city}, ${s2.country}`,
          },
        ],
      },
      {
        title: 'Ubezpieczenie i osoba kontaktowa',
        icon: 'pi pi-clipboard',
        items: [
          { label: 'Status ubezpieczenia', value: INSURANCE_STATUS_LABELS[s3.insuranceStatus] },
          { label: 'Płatnik', value: s3.insurancePayer },
          {
            label: 'Oddział NFZ',
            value: NFZ_BRANCH_OPTIONS.find((b) => b.value === s3.nfzBranch)?.label ?? null,
          },
          { label: 'Grupa krwi', value: s3.bloodType },
          { label: 'Osoba kontaktowa', value: s3.contactFullName || null },
          { label: 'Telefon kontaktowy', value: s3.contactPhone || null },
        ],
      },
    ];

    if (this.mode() === 'create') {
      sections.push({
        title: 'Przyjęcie',
        icon: 'pi pi-calendar-clock',
        items:
          s4.admissionType === 'outpatient'
            ? [{ label: 'Typ przyjęcia', value: ADMISSION_TYPE_LABELS[s4.admissionType] }]
            : [
                { label: 'Typ przyjęcia', value: ADMISSION_TYPE_LABELS[s4.admissionType] },
                { label: 'Oddział', value: this.wardService.nameOf(s4.wardId) },
                {
                  label: 'Sala/Łóżko',
                  value: [s4.room, s4.bed].filter(Boolean).join(' / ') || null,
                },
                {
                  label: 'Lekarz prowadzący',
                  value: this.staffService.nameOf(s4.attendingPhysicianId),
                },
                { label: 'Triage', value: s4.triageLevel ? TRIAGE_LABELS[s4.triageLevel] : null },
                { label: 'Powód przyjęcia', value: s4.reason || null },
              ],
      });
    }

    return sections;
  };

  protected submit(): void {
    if (this.duplicatePatient()) return;
    this.submitting.set(true);

    const s1 = this.step1.getRawValue();
    const s2 = this.step2.getRawValue();
    const s3 = this.step3.getRawValue();
    const s4 = this.step4.getRawValue();

    const birthDate = s1.birthDate ? toIsoDate(s1.birthDate) : '';

    const status: AdmissionStatus =
      this.mode() === 'edit'
        ? (this.originalPatient?.status ?? 'registered')
        : s4.admissionType === 'outpatient'
          ? 'outpatient'
          : 'registered';

    const draft: PatientDraft = {
      pesel: s1.noPesel ? null : s1.pesel || null,
      noPeselReason: s1.noPesel ? (s1.noPeselReason ?? undefined) : undefined,
      identityDocument:
        s1.noPesel && s1.documentType
          ? { type: s1.documentType, number: s1.documentNumber }
          : undefined,
      firstName: s2.firstName,
      secondName: s2.secondName || undefined,
      lastName: s2.lastName,
      birthDate,
      gender: s1.gender,
      phone: s2.phone || undefined,
      email: s2.email || undefined,
      address: {
        street: s2.street,
        buildingNumber: s2.buildingNumber,
        apartmentNumber: s2.apartmentNumber || undefined,
        postalCode: s2.postalCode,
        city: s2.city,
        country: s2.country,
      },
      emergencyContact: s3.contactFullName
        ? {
            fullName: s3.contactFullName,
            relation: s3.contactRelation,
            phone: s3.contactPhone,
            isLegalGuardian: s3.isLegalGuardian,
          }
        : undefined,
      insurance: {
        status: s3.insuranceStatus,
        nfzBranch: s3.nfzBranch,
        payer: s3.insurancePayer,
        ewusVerifiedAt: s3.ewusVerifiedAt ?? undefined,
      },
      bloodType: (s3.bloodType as PatientDraft['bloodType']) ?? undefined,
      status,
      currentAdmission: this.originalPatient?.currentAdmission,
      flags: this.originalPatient?.flags ?? [],
    };

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
        error: () => {
          this.submitting.set(false);
          this.messageService.add({
            severity: 'error',
            summary: 'Nie udało się zapisać zmian',
          });
        },
      });
      return;
    }

    const needsAdmission = s4.admissionType !== 'outpatient';

    this.patientService
      .createPatient(draft)
      .pipe(
        switchMap((patient) => {
          if (!needsAdmission) return of(patient);
          const admission: Admission = {
            admissionType: s4.admissionType,
            admittedAt: s4.admittedAt.toISOString(),
            wardId: s4.wardId,
            room: s4.room || undefined,
            bed: s4.bed || undefined,
            attendingPhysicianId: s4.attendingPhysicianId,
            triageLevel: s4.triageLevel ?? undefined,
            reason: s4.reason,
            referralNumber: s4.referralNumber || undefined,
          };
          return this.patientService.admitPatient(patient.id, admission);
        }),
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
        error: () => {
          this.submitting.set(false);
          this.messageService.add({
            severity: 'error',
            summary: 'Nie udało się zarejestrować pacjenta',
          });
        },
      });
  }

  protected cancel(): void {
    if (this.mode() === 'edit' && this.patientId()) {
      this.router.navigate(['/patients', this.patientId(), 'overview']);
    } else {
      this.router.navigate(['/patients']);
    }
  }

  private prefill(patient: Patient): void {
    this.step1.reset({
      noPesel: !patient.pesel,
      pesel: patient.pesel ?? '',
      birthDate: fromIsoDate(patient.birthDate),
      gender: patient.gender,
      noPeselReason: patient.noPeselReason ?? null,
      documentType: patient.identityDocument?.type ?? null,
      documentNumber: patient.identityDocument?.number ?? '',
    });

    this.step2.reset({
      firstName: patient.firstName,
      secondName: patient.secondName ?? '',
      lastName: patient.lastName,
      phone: patient.phone ?? '',
      email: patient.email ?? '',
      street: patient.address.street,
      buildingNumber: patient.address.buildingNumber,
      apartmentNumber: patient.address.apartmentNumber ?? '',
      postalCode: patient.address.postalCode,
      city: patient.address.city,
      country: patient.address.country,
    });

    this.step3.reset({
      insuranceStatus: patient.insurance.status,
      insurancePayer: patient.insurance.payer,
      nfzBranch: patient.insurance.nfzBranch,
      ewusVerifiedAt: patient.insurance.ewusVerifiedAt ?? null,
      contactFullName: patient.emergencyContact?.fullName ?? '',
      contactRelation: patient.emergencyContact?.relation ?? '',
      contactPhone: patient.emergencyContact?.phone ?? '',
      isLegalGuardian: patient.emergencyContact?.isLegalGuardian ?? false,
      bloodType: patient.bloodType ?? null,
    });

    if (patient.currentAdmission) {
      const a = patient.currentAdmission;
      this.step4.reset({
        admissionType: a.admissionType,
        wardId: a.wardId,
        room: a.room ?? '',
        bed: a.bed ?? '',
        attendingPhysicianId: a.attendingPhysicianId,
        triageLevel: a.triageLevel ?? null,
        reason: a.reason,
        referralNumber: a.referralNumber ?? '',
        admittedAt: new Date(a.admittedAt),
      });
    }
  }

  hasUnsavedChanges(): boolean {
    if (this.submitted()) return false;
    return this.step1.dirty || this.step2.dirty || this.step3.dirty || this.step4.dirty;
  }
}
