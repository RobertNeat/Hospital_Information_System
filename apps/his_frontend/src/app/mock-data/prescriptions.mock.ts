import type { Prescription } from '../models';
import { PRESCRIPTIONS_PART_1 } from './prescriptions.part-1.mock';
import { PRESCRIPTIONS_PART_2 } from './prescriptions.part-2.mock';

export const PRESCRIPTIONS: Prescription[] = [...PRESCRIPTIONS_PART_1, ...PRESCRIPTIONS_PART_2];
