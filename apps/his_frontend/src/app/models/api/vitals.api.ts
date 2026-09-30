import type { ID } from '../common.model';
import type { VitalAnomaly, VitalSigns, VitalSignsDraft, VitalsRange } from '../vitals.model';

/** `POST /patients/{id}/vitals`. */
export type VitalSignsCreateRequest = VitalSignsDraft;

/** Response of recording a reading; `anomalies` are computed by the server. */
export interface VitalsRecordResponse {
  saved: VitalSigns;
  anomalies: VitalAnomaly[];
}

export interface VitalsQuery {
  range?: VitalsRange;
}

export interface WardVitalsQuery {
  wardId?: ID;
}
