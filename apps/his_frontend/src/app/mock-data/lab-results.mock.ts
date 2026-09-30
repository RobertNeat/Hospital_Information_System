import type { LabResult } from '../models';
import { LAB_RESULTS_PAT_001 } from './lab-results.pat-001.mock';
import { LAB_RESULTS_PAT_004_012 } from './lab-results.pat-004-012.mock';
import { LAB_RESULTS_ACUTE_PREOP } from './lab-results.acute-preop.mock';

export const LAB_RESULTS: LabResult[] = [
  ...LAB_RESULTS_PAT_001,
  ...LAB_RESULTS_PAT_004_012,
  ...LAB_RESULTS_ACUTE_PREOP,
];
