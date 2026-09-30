/** Opaque identifier (Java: `UUID` in the database, serialized as a string). The client never parses it. */
export type ID = string;
/** Calendar date `YYYY-MM-DD` (Java: `LocalDate`). */
export type ISODate = string;
/** UTC instant with a trailing `Z`, e.g. `2026-01-31T10:15:00Z` (Java: `Instant`). */
export type ISODateTime = string;

/** Optional projection of a referenced entity; the foreign key itself is always `xxxId: ID`. */
export interface Ref {
  id: ID;
  display: string;
}

/** Audit fields filled by the backend from the session; the client never sends them. */
export interface Auditable {
  createdAt: ISODateTime;
  createdById?: ID;
  updatedAt?: ISODateTime;
  updatedById?: ID;
}

/** Optimistic locking counter (Java: JPA `@Version`); a stale write ends with 409 `ProblemDetail`. */
export interface Versioned {
  version?: number;
}

export type CodingSystem = 'ICD-10' | 'LOINC' | 'ATC' | 'ICD-9-PL' | 'local';

/** FHIR-compatible gender code. */
export type Gender = 'female' | 'male' | 'other' | 'unknown';

export interface Coding {
  system: CodingSystem;
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
