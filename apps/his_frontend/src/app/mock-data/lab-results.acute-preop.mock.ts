import type { LabResult } from '../models';
import { daysAgo, hoursAgo } from './mock-utils';

export const LAB_RESULTS_ACUTE_PREOP: LabResult[] = [
  // pat-002: troponin (critical, ACS)
  {
    id: 'lres-016',
    patientId: 'pat-002',
    orderId: 'lord-002',
    testCode: 'TROP',
    testName: 'Troponina I hs',
    category: 'biochemistry',
    collectedAt: daysAgo(1, 10),
    resultedAt: hoursAgo(19),
    status: 'final',
    performerName: 'lek. med. lab. Bartosz Kowalczyk',
    observations: [
      {
        analyteCode: 'TROP',
        analyteName: 'Troponina I hs',
        value: 4520,
        unit: 'ng/L',
        referenceRange: { low: 0, high: 14 },
        flag: 'HH',
      },
    ],
    comment: 'Wynik krytyczny, zgodny z ostrym zawałem serca.',
  },
  {
    id: 'lres-017',
    patientId: 'pat-002',
    orderId: 'lord-002',
    testCode: 'CKMB',
    testName: 'CK-MB',
    category: 'biochemistry',
    collectedAt: daysAgo(1, 10),
    resultedAt: hoursAgo(19),
    status: 'final',
    performerName: 'lek. med. lab. Bartosz Kowalczyk',
    observations: [
      {
        analyteCode: 'CKMB',
        analyteName: 'CK-MB',
        value: 78,
        unit: 'U/L',
        referenceRange: { low: 0, high: 25 },
        flag: 'HH',
      },
    ],
  },

  // pat-013: CRP series over recent hospitalisation, sample for observations render
  {
    id: 'lres-018',
    patientId: 'pat-013',
    testCode: 'CRP',
    testName: 'CRP',
    category: 'biochemistry',
    collectedAt: hoursAgo(2),
    resultedAt: hoursAgo(1),
    status: 'preliminary',
    performerName: 'lek. med. lab. Bartosz Kowalczyk',
    observations: [
      {
        analyteCode: 'CRP',
        analyteName: 'Białko C-reaktywne',
        value: 62,
        unit: 'mg/L',
        referenceRange: { low: 0, high: 5 },
        flag: 'H',
      },
    ],
  },

  // pat-009: pre-op panel
  {
    id: 'lres-019',
    patientId: 'pat-009',
    orderId: 'lord-003',
    testCode: 'MORF',
    testName: 'Morfologia krwi z rozmazem',
    category: 'hematology',
    collectedAt: daysAgo(2, 7),
    resultedAt: daysAgo(1, 14),
    status: 'final',
    performerName: 'lek. med. lab. Alicja Ostrowska',
    observations: [
      {
        analyteCode: 'WBC',
        analyteName: 'Leukocyty',
        value: 6.8,
        unit: 'tys/uL',
        referenceRange: { low: 4.0, high: 10.0 },
        flag: 'N',
      },
      {
        analyteCode: 'HGB',
        analyteName: 'Hemoglobina',
        value: 14.5,
        unit: 'g/dL',
        referenceRange: { low: 12.0, high: 16.0 },
        flag: 'N',
      },
      {
        analyteCode: 'PLT',
        analyteName: 'Płytki krwi',
        value: 260,
        unit: 'tys/uL',
        referenceRange: { low: 150, high: 400 },
        flag: 'N',
      },
    ],
  },
  {
    id: 'lres-020',
    patientId: 'pat-009',
    orderId: 'lord-003',
    testCode: 'INRPT',
    testName: 'INR/PT',
    category: 'coagulation',
    collectedAt: daysAgo(2, 7),
    resultedAt: daysAgo(1, 14),
    status: 'final',
    performerName: 'lek. med. lab. Alicja Ostrowska',
    observations: [
      {
        analyteCode: 'INR',
        analyteName: 'INR',
        value: 1.0,
        unit: '',
        referenceRange: { low: 0.8, high: 1.2 },
        flag: 'N',
      },
      {
        analyteCode: 'PT',
        analyteName: 'Czas protrombinowy',
        value: 12.5,
        unit: 's',
        referenceRange: { low: 11, high: 15 },
        flag: 'N',
      },
    ],
  },
];
