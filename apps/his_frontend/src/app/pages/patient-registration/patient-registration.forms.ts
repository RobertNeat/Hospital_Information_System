import { type NonNullableFormBuilder, Validators } from '@angular/forms';

import { peselValidator } from '../../validators/pesel.validator';
import { postalCodeValidator } from '../../validators/postal-code.validator';
import { phoneValidator } from '../../validators/phone.validator';
import type { AdmissionType, Gender, NoPeselReason, Patient, TriageLevel } from '../../models';

/** Formats a JS `Date` as a local `YYYY-MM-DD` ISO date string (never UTC-shifted). */
export function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function fromIsoDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

// ---- Step 1: Identyfikacja ----
export function createIdentityForm(fb: NonNullableFormBuilder) {
  return fb.group({
    noPesel: fb.control(false),
    pesel: fb.control('', [Validators.required, peselValidator()]),
    birthDate: fb.control<Date | null>(null, Validators.required),
    gender: fb.control<Gender>('unknown'),
    noPeselReason: fb.control<NoPeselReason | null>(null),
    documentType: fb.control<'id_card' | 'passport' | 'other' | null>(null),
    documentNumber: fb.control(''),
  });
}

// ---- Step 2: Dane osobowe i kontaktowe ----
export function createPersonalForm(fb: NonNullableFormBuilder) {
  return fb.group({
    firstName: fb.control('', Validators.required),
    secondName: fb.control(''),
    lastName: fb.control('', Validators.required),
    phone: fb.control('', [phoneValidator()]),
    email: fb.control('', [Validators.email]),
    street: fb.control('', Validators.required),
    buildingNumber: fb.control('', Validators.required),
    apartmentNumber: fb.control(''),
    postalCode: fb.control('', [Validators.required, postalCodeValidator()]),
    city: fb.control('', Validators.required),
    country: fb.control('Polska', Validators.required),
  });
}

// ---- Step 3: Ubezpieczenie i osoba kontaktowa ----
export function createInsuranceForm(fb: NonNullableFormBuilder) {
  return fb.group({
    insuranceStatus: fb.control<'active' | 'inactive' | 'unknown'>('unknown'),
    insurancePayer: fb.control<'NFZ' | 'private' | 'none'>('NFZ'),
    nfzBranch: fb.control(''),
    ewusVerifiedAt: fb.control<string | null>(null),
    contactFullName: fb.control(''),
    contactRelation: fb.control(''),
    contactPhone: fb.control(''),
    isLegalGuardian: fb.control(false),
    bloodType: fb.control<string | null>(null),
  });
}

// ---- Step 4: Przyjęcie ----
export function createAdmissionForm(fb: NonNullableFormBuilder) {
  return fb.group({
    admissionType: fb.control<AdmissionType>('planned'),
    wardId: fb.control(''),
    room: fb.control(''),
    bed: fb.control(''),
    attendingPhysicianId: fb.control(''),
    triageLevel: fb.control<TriageLevel | null>(null),
    reason: fb.control(''),
    referralNumber: fb.control(''),
    admittedAt: fb.control<Date>(new Date()),
  });
}

export type IdentityForm = ReturnType<typeof createIdentityForm>;
export type PersonalForm = ReturnType<typeof createPersonalForm>;
export type InsuranceForm = ReturnType<typeof createInsuranceForm>;
export type AdmissionForm = ReturnType<typeof createAdmissionForm>;

export interface RegistrationForms {
  step1: IdentityForm;
  step2: PersonalForm;
  step3: InsuranceForm;
  step4: AdmissionForm;
}

/** PESEL <-> manual-birth-date branch toggling (`birthDate` stays required in both). */
export function applyNoPesel(form: IdentityForm, noPesel: boolean): void {
  if (noPesel) {
    form.controls.pesel.disable({ emitEvent: false });
    form.controls.noPeselReason.setValidators(Validators.required);
  } else {
    form.controls.pesel.enable({ emitEvent: false });
    form.controls.noPeselReason.clearValidators();
  }
  form.controls.noPeselReason.updateValueAndValidity({ emitEvent: false });
}

/** Enables/disables and re-validates admission controls for the chosen admission type. */
export function applyAdmissionType(form: AdmissionForm, type: AdmissionType): void {
  const controls = form.controls;
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

/** Resets all step forms from a loaded patient (edit mode). */
export function prefillForms(forms: RegistrationForms, patient: Patient): void {
  forms.step1.reset({
    noPesel: !patient.pesel,
    pesel: patient.pesel ?? '',
    birthDate: fromIsoDate(patient.birthDate),
    gender: patient.gender,
    noPeselReason: patient.noPeselReason ?? null,
    documentType: patient.identityDocument?.type ?? null,
    documentNumber: patient.identityDocument?.number ?? '',
  });

  forms.step2.reset({
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

  forms.step3.reset({
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
    forms.step4.reset({
      admissionType: a.admissionType,
      wardId: a.wardId ?? '',
      room: a.room ?? '',
      bed: a.bed ?? '',
      attendingPhysicianId: a.attendingPhysicianId ?? '',
      triageLevel: a.triageLevel ?? null,
      reason: a.reason ?? '',
      referralNumber: a.referralNumber ?? '',
      admittedAt: new Date(a.admittedAt),
    });
  }
}
