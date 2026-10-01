/** Base path of the REST API; proxied to the backend by the dev server and by nginx. */
export const API_BASE_URL = '/api/v1';

export const AUTH_LOGIN_URL = `${API_BASE_URL}/auth/login`;
export const AUTH_REGISTER_URL = `${API_BASE_URL}/auth/register`;
export const AUTH_LOGOUT_URL = `${API_BASE_URL}/auth/logout`;
export const AUTH_ME_URL = `${API_BASE_URL}/auth/me`;

// Path ids are opaque to the backend; encode them defensively.
const enc = encodeURIComponent;

// Staff, wards, dashboard.
export const STAFF_URL = `${API_BASE_URL}/staff`;
export const staffUrl = (staffId: string): string => `${STAFF_URL}/${enc(staffId)}`;
export const staffActivateUrl = (staffId: string): string => `${staffUrl(staffId)}/activate`;
export const staffLockUrl = (staffId: string): string => `${staffUrl(staffId)}/lock`;
export const WARDS_URL = `${API_BASE_URL}/wards`;
export const DASHBOARD_STATS_URL = `${API_BASE_URL}/dashboard/stats`;

// Patients, admissions.
export const PATIENTS_URL = `${API_BASE_URL}/patients`;
export const PATIENT_DUPLICATE_CHECK_URL = `${PATIENTS_URL}/duplicate-check`;
export const patientUrl = (patientId: string): string => `${PATIENTS_URL}/${enc(patientId)}`;
/** Sub-resource of a patient, e.g. `patientSubUrl(id, 'vitals/latest')`. */
export const patientSubUrl = (patientId: string, sub: string): string =>
  `${patientUrl(patientId)}/${sub}`;
export const patientAdmissionsUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'admissions');
export const patientDischargeUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'discharge');

// EHR (per patient).
export const patientEhrSummaryUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'ehr-summary');
export const patientEncountersUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'encounters');
export const patientEpisodesUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'episodes');
export const patientClinicalNotesUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'clinical-notes');
export const patientDiagnosesUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'diagnoses');
export const patientAllergiesUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'allergies');
export const patientContraindicationsUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'contraindications');
export const patientTreatmentsUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'treatments');
export const ICD10_URL = `${API_BASE_URL}/dictionaries/icd-10`;

// Laboratory.
export const LAB_TESTS_URL = `${API_BASE_URL}/lab-tests`;
export const LAB_PANELS_URL = `${API_BASE_URL}/lab-panels`;
export const LAB_ORDERS_URL = `${API_BASE_URL}/lab-orders`;
export const labOrderUrl = (orderId: string): string => `${LAB_ORDERS_URL}/${enc(orderId)}`;
export const labOrderStatusUrl = (orderId: string): string => `${labOrderUrl(orderId)}/status`;
export const labOrderCancelUrl = (orderId: string): string => `${labOrderUrl(orderId)}/cancel`;
export const patientLabOrdersUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'lab-orders');
export const patientLabResultsUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'lab-results');
export const patientLabAnalytesUrl = (patientId: string): string =>
  `${patientLabResultsUrl(patientId)}/analytes`;
export const patientLabTrendUrl = (patientId: string, analyteCode: string): string =>
  `${patientLabResultsUrl(patientId)}/trends/${enc(analyteCode)}`;
export const LAB_RESULTS_URL = `${API_BASE_URL}/lab-results`;
export const labResultUrl = (resultId: string): string => `${LAB_RESULTS_URL}/${enc(resultId)}`;
export const labResultAcknowledgeUrl = (resultId: string): string =>
  `${labResultUrl(resultId)}/acknowledge`;

// Imaging.
export const IMAGING_EXAMS_URL = `${API_BASE_URL}/imaging-exams`;
export const IMAGING_SLOTS_URL = `${API_BASE_URL}/imaging-slots`;
export const IMAGING_ORDERS_URL = `${API_BASE_URL}/imaging-orders`;
export const imagingOrderUrl = (orderId: string): string => `${IMAGING_ORDERS_URL}/${enc(orderId)}`;
export const imagingOrderStatusUrl = (orderId: string): string =>
  `${imagingOrderUrl(orderId)}/status`;
export const imagingOrderCancelUrl = (orderId: string): string =>
  `${imagingOrderUrl(orderId)}/cancel`;
export const patientImagingOrdersUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'imaging-orders');
export const patientImagingResultsUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'imaging-results');
export const IMAGING_RESULTS_URL = `${API_BASE_URL}/imaging-results`;
export const imagingResultUrl = (resultId: string): string =>
  `${IMAGING_RESULTS_URL}/${enc(resultId)}`;
export const imagingResultAcknowledgeUrl = (resultId: string): string =>
  `${imagingResultUrl(resultId)}/acknowledge`;

// Drugs, prescriptions, safety checks.
export const DRUGS_URL = `${API_BASE_URL}/drugs`;
export const drugUrl = (drugId: string): string => `${DRUGS_URL}/${enc(drugId)}`;
export const PRESCRIPTIONS_URL = `${API_BASE_URL}/prescriptions`;
export const prescriptionUrl = (prescriptionId: string): string =>
  `${PRESCRIPTIONS_URL}/${enc(prescriptionId)}`;
export const prescriptionCancelUrl = (prescriptionId: string): string =>
  `${prescriptionUrl(prescriptionId)}/cancel`;
export const patientPrescriptionsUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'prescriptions');
export const patientActiveMedicationsUrl = (patientId: string): string =>
  patientSubUrl(patientId, 'active-medications');
export const DRUG_SAFETY_CHECKS_URL = `${API_BASE_URL}/drug-safety-checks`;

// Vital signs.
export const patientVitalsUrl = (patientId: string): string => patientSubUrl(patientId, 'vitals');
export const patientVitalsLatestUrl = (patientId: string): string =>
  `${patientVitalsUrl(patientId)}/latest`;
export const VITALS_WARD_OVERVIEW_URL = `${API_BASE_URL}/vitals/ward-overview`;
export const VITAL_THRESHOLDS_URL = `${API_BASE_URL}/vital-thresholds`;

// Messaging, tasks, handoff, alerts.
export const MESSAGE_THREADS_URL = `${API_BASE_URL}/message-threads`;
export const messageThreadUrl = (threadId: string): string =>
  `${MESSAGE_THREADS_URL}/${enc(threadId)}`;
export const threadMessagesUrl = (threadId: string): string =>
  `${messageThreadUrl(threadId)}/messages`;
export const threadReadUrl = (threadId: string): string => `${messageThreadUrl(threadId)}/read`;
export const TASKS_URL = `${API_BASE_URL}/tasks`;
export const taskStatusUrl = (taskId: string): string => `${TASKS_URL}/${enc(taskId)}/status`;
export const HANDOFF_NOTES_URL = `${API_BASE_URL}/handoff-notes`;
export const ALERTS_URL = `${API_BASE_URL}/alerts`;
export const alertAcknowledgeUrl = (alertId: string): string =>
  `${ALERTS_URL}/${enc(alertId)}/acknowledge`;

// Terminology (SNOMED CT).
export const SNOMED_CONCEPTS_URL = `${API_BASE_URL}/terminology/snomed/concepts`;
export const snomedConceptUrl = (sctid: string): string => `${SNOMED_CONCEPTS_URL}/${enc(sctid)}`;
