import type { Contraindication } from '../models';
import { daysAgo } from './mock-utils';

export const CONTRAINDICATIONS: Contraindication[] = [
  {
    id: 'ctr-001',
    patientId: 'pat-001',
    description: 'Niesteroidowe leki przeciwzapalne (NLPZ)',
    reason: 'Niewydolność serca – ryzyko retencji płynów i zaostrzenia objawów',
    recordedAt: daysAgo(300),
  },
  {
    id: 'ctr-002',
    patientId: 'pat-001',
    description: 'Antybiotyki beta-laktamowe',
    reason: 'Udokumentowany wstrząs anafilaktyczny po penicylinie',
    recordedAt: daysAgo(400),
  },
  {
    id: 'ctr-003',
    patientId: 'pat-002',
    description: 'Jodowe środki kontrastowe',
    reason: 'Ciężka reakcja alergiczna w wywiadzie (pokrzywka, duszność)',
    recordedAt: daysAgo(600),
  },
  {
    id: 'ctr-004',
    patientId: 'pat-004',
    description: 'Leki przeciwzakrzepowe w dawkach pełnych',
    reason: 'Podwyższone ryzyko krwawienia – wiek podeszły',
    recordedAt: daysAgo(90),
  },
];
