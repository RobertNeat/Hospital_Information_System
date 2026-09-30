import { Pipe, type PipeTransform } from '@angular/core';
import * as labels from '../constants/labels';

/**
 * Looks up the Polish label for an enum-like value, e.g. `status | label:'admissionStatus'`.
 * The second argument is the label-map key without its `_LABELS` suffix, camelCase,
 * e.g. 'admissionStatus' -> `ADMISSION_STATUS_LABELS`, 'urgency' -> `URGENCY_LABELS`.
 */
const LABEL_MAPS = {
  admissionStatus: labels.ADMISSION_STATUS_LABELS,
  triage: labels.TRIAGE_LABELS,
  patientFlag: labels.PATIENT_FLAG_LABELS,
  gender: labels.GENDER_LABELS,
  noPeselReason: labels.NO_PESEL_REASON_LABELS,
  identityDocumentType: labels.IDENTITY_DOCUMENT_TYPE_LABELS,
  insuranceStatus: labels.INSURANCE_STATUS_LABELS,
  insurancePayer: labels.INSURANCE_PAYER_LABELS,
  admissionType: labels.ADMISSION_TYPE_LABELS,
  encounterType: labels.ENCOUNTER_TYPE_LABELS,
  encounterStatus: labels.ENCOUNTER_STATUS_LABELS,
  noteCategory: labels.NOTE_CATEGORY_LABELS,
  diagnosisType: labels.DIAGNOSIS_TYPE_LABELS,
  diagnosisStatus: labels.DIAGNOSIS_STATUS_LABELS,
  allergyCategory: labels.ALLERGY_CATEGORY_LABELS,
  allergySeverity: labels.ALLERGY_SEVERITY_LABELS,
  allergyStatus: labels.ALLERGY_STATUS_LABELS,
  treatmentType: labels.TREATMENT_TYPE_LABELS,
  treatmentStatus: labels.TREATMENT_STATUS_LABELS,
  urgency: labels.URGENCY_LABELS,
  specimen: labels.SPECIMEN_LABELS,
  labCategory: labels.LAB_CATEGORY_LABELS,
  orderStatus: labels.ORDER_STATUS_LABELS,
  resultFlag: labels.RESULT_FLAG_LABELS,
  resultStatus: labels.RESULT_STATUS_LABELS,
  imagingModality: labels.IMAGING_MODALITY_LABELS,
  laterality: labels.LATERALITY_LABELS,
  drugForm: labels.DRUG_FORM_LABELS,
  route: labels.ROUTE_LABELS,
  reimbursement: labels.REIMBURSEMENT_LABELS,
  frequency: labels.FREQUENCY_LABELS,
  timeOfDay: labels.TIME_OF_DAY_LABELS,
  prescriptionStatus: labels.PRESCRIPTION_STATUS_LABELS,
  prescriptionKind: labels.PRESCRIPTION_KIND_LABELS,
  vitalContext: labels.VITAL_CONTEXT_LABELS,
  priority: labels.PRIORITY_LABELS,
  taskStatus: labels.TASK_STATUS_LABELS,
  shift: labels.SHIFT_LABELS,
  alertType: labels.ALERT_TYPE_LABELS,
  alertSeverity: labels.ALERT_SEVERITY_LABELS,
  staffRole: labels.STAFF_ROLE_LABELS,
} satisfies Record<string, Record<string, string>>;

/** Valid second arguments to `| label:'...'`, e.g. 'urgency', 'admissionStatus'. */
export type LabelMapKey = keyof typeof LABEL_MAPS;

@Pipe({ name: 'label' })
export class LabelPipe implements PipeTransform {
  transform(value: string | null | undefined, mapKey: LabelMapKey): string {
    if (value === null || value === undefined) return '';
    const map: Record<string, string> = LABEL_MAPS[mapKey];
    return map[value] ?? value;
  }
}
