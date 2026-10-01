import { formatDate } from '@angular/common';

import type { RegistrationSummarySection } from '../../components/registration-summary/registration-summary';
import {
  ADMISSION_TYPE_LABELS,
  GENDER_LABELS,
  IDENTITY_DOCUMENT_TYPE_LABELS,
  INSURANCE_STATUS_LABELS,
  NFZ_BRANCH_OPTIONS,
  NO_PESEL_REASON_LABELS,
  TRIAGE_LABELS,
} from '../../constants/labels';
import type { AdmitPatientRequest, Patient, PatientDraft } from '../../models';
import type { PatientUpdateRequest } from '../../models/api';
import {
  toIsoDate,
  type AdmissionForm,
  type IdentityForm,
  type InsuranceForm,
  type PersonalForm,
} from './patient-registration.forms';

export interface RegistrationValues {
  s1: ReturnType<IdentityForm['getRawValue']>;
  s2: ReturnType<PersonalForm['getRawValue']>;
  s3: ReturnType<InsuranceForm['getRawValue']>;
  s4: ReturnType<AdmissionForm['getRawValue']>;
  mode: 'create' | 'edit';
}

export interface SummaryInput extends RegistrationValues {
  locale: string;
  wardName: (id: string) => string;
  staffName: (id: string) => string;
}

export interface DraftInput extends RegistrationValues {
  original: Patient | null;
}

/** Builds the read-only review sections shown in the wizard summary. */
export function buildSummarySections(input: SummaryInput): RegistrationSummarySection[] {
  const { s1, s2, s3, s4, locale } = input;
  const birthDate = s1.birthDate ? formatDate(toIsoDate(s1.birthDate), 'dd.MM.yyyy', locale) : null;

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
            { label: 'Data urodzenia', value: birthDate },
            { label: 'Płeć', value: GENDER_LABELS[s1.gender] },
          ]
        : [
            { label: 'PESEL', value: s1.pesel || null },
            { label: 'Data urodzenia', value: birthDate },
            { label: 'Płeć', value: GENDER_LABELS[s1.gender] },
          ],
    },
    {
      title: 'Dane osobowe i kontaktowe',
      icon: 'pi pi-users',
      items: [
        {
          label: 'Imię i nazwisko',
          value: `${s2.firstName} ${s2.secondName ? s2.secondName + ' ' : ''}${s2.lastName}`.trim(),
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

  if (input.mode === 'create') {
    sections.push({
      title: 'Przyjęcie',
      icon: 'pi pi-calendar-clock',
      items:
        s4.admissionType === 'outpatient'
          ? [{ label: 'Typ przyjęcia', value: ADMISSION_TYPE_LABELS[s4.admissionType] }]
          : [
              { label: 'Typ przyjęcia', value: ADMISSION_TYPE_LABELS[s4.admissionType] },
              { label: 'Oddział', value: input.wardName(s4.wardId) },
              { label: 'Sala/Łóżko', value: [s4.room, s4.bed].filter(Boolean).join(' / ') || null },
              { label: 'Lekarz prowadzący', value: input.staffName(s4.attendingPhysicianId) },
              { label: 'Triage', value: s4.triageLevel ? TRIAGE_LABELS[s4.triageLevel] : null },
              { label: 'Powód przyjęcia', value: s4.reason || null },
            ],
    });
  }

  return sections;
}

/** Maps the raw step-form values to the patient payload sent to the API. */
export function buildPatientDraft({ s1, s2, s3, original }: DraftInput): PatientDraft {
  return {
    pesel: s1.noPesel ? null : s1.pesel || null,
    noPeselReason: s1.noPesel ? (s1.noPeselReason ?? undefined) : undefined,
    identityDocument:
      s1.noPesel && s1.documentType
        ? { type: s1.documentType, number: s1.documentNumber }
        : undefined,
    firstName: s2.firstName,
    secondName: s2.secondName || undefined,
    lastName: s2.lastName,
    birthDate: s1.birthDate ? toIsoDate(s1.birthDate) : '',
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
    flags: original?.flags ?? [],
  };
}

/** Optional fields the edit form can empty; PATCH clears them only when sent as `null`. */
const CLEARABLE_FIELDS = [
  'noPeselReason',
  'identityDocument',
  'secondName',
  'phone',
  'email',
  'emergencyContact',
  'bloodType',
] as const;

/** PATCH payload for edit mode: emptied optional fields as `null`, plus the loaded `version`. */
export function buildPatientUpdate(input: DraftInput): PatientUpdateRequest {
  const draft = buildPatientDraft(input);
  const update: PatientUpdateRequest = { ...draft, version: input.original?.version };
  for (const key of CLEARABLE_FIELDS) {
    update[key] ??= null;
  }
  return update;
}

export function buildAdmission(s4: RegistrationValues['s4']): AdmitPatientRequest {
  // Outpatient admission carries only type and time (no ward stay); the time is submit time.
  if (s4.admissionType === 'outpatient') {
    return { admissionType: 'outpatient', admittedAt: new Date().toISOString() };
  }
  return {
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
}
