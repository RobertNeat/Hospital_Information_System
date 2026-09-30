import type { Patient } from '../models';
import { PATIENTS_PART_1 } from './patients.part-1.mock';
import { PATIENTS_PART_2 } from './patients.part-2.mock';
import { PATIENTS_PART_3 } from './patients.part-3.mock';

export const PATIENTS: Patient[] = [...PATIENTS_PART_1, ...PATIENTS_PART_2, ...PATIENTS_PART_3];
