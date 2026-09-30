import type { Drug } from '../models';
import { DRUGS_PART_1 } from './drugs.part-1.mock';
import { DRUGS_PART_2 } from './drugs.part-2.mock';

export const DRUGS: Drug[] = [...DRUGS_PART_1, ...DRUGS_PART_2];
