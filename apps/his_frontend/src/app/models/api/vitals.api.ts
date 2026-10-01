import type { ID, ISODateTime } from '../common.model';
import type { VitalAnomaly, VitalSigns, VitalsRange } from '../vitals.model';

/**
 * `POST /patients/{id}/vitals`. The actor comes from the token (`recordedById` is not sent);
 * `recordedAt` defaults to "now" on the server and must not be in the future.
 */
export type VitalSignsCreateRequest = Omit<VitalSigns, 'id' | 'recordedById' | 'recordedAt'> & {
  recordedAt?: ISODateTime;
};

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
