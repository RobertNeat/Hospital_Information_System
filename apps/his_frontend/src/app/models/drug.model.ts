import type { ID } from './common.model';

export type DrugForm =
  | 'tablet'
  | 'capsule'
  | 'injection'
  | 'syrup'
  | 'drops'
  | 'ointment'
  | 'inhaler'
  | 'suppository'
  | 'patch';

export type AdministrationRoute =
  'oral' | 'sublingual' | 'iv' | 'im' | 'sc' | 'topical' | 'inhalation' | 'rectal' | 'transdermal';

/** Labels: 100%, 50%, 30%, ryczałt, bezpłatny, pełnopłatny. */
export type ReimbursementLevel = '100%' | '50%' | '30%' | 'R' | 'B' | 'none';

export interface DoseQuantity {
  value: number;
  unit: string;
}

/** Drug catalog entry. Read-only from the UI point of view; managed by the backend. */
export interface Drug {
  id: ID;
  /** Trade name. */
  name: string;
  activeSubstance: string;
  atcCode: string;
  form: DrugForm;
  strength: string;
  packageSize: number;
  packageUnit: string;
  routes: AdministrationRoute[];
  defaultDoseUnit: string;
  rxOnly: boolean;
  reimbursementOptions: ReimbursementLevel[];
  interactsWithAtc?: string[];
  maxDailyDose?: DoseQuantity;
}
