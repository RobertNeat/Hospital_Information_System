export type ID = string;
export type ISODate = string;
export type ISODateTime = string;

/** FHIR-compatible gender code. */
export type Gender = 'female' | 'male' | 'other' | 'unknown';

export interface Coding {
  system: 'ICD-10' | 'LOINC' | 'ATC' | 'ICD-9-PL' | 'local';
  code: string;
  display: string;
}

export interface Address {
  street: string;
  buildingNumber: string;
  apartmentNumber?: string;
  postalCode: string;
  city: string;
  country: string;
}

export interface SelectOption<T = string> {
  label: string;
  value: T;
}

export type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/** Severity for `MessageService.add()` toasts -- PrimeNG toasts use `'error'`, not `'danger'`. */
export type ToastSeverity = 'success' | 'info' | 'warn' | 'error';

export type Priority = 'normal' | 'high' | 'critical';
