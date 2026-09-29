import type { ClinicalNote } from '../models';
import { daysAgo, hoursAgo } from './mock-utils';

export const CLINICAL_NOTES: ClinicalNote[] = [
  {
    id: 'note-001',
    patientId: 'pat-001',
    encounterId: 'enc-001',
    authorId: 'stf-001',
    createdAt: daysAgo(3),
    category: 'admission',
    title: 'Przyjęcie do oddziału',
    content:
      'Pacjent przyjęty z powodu duszności wysiłkowej i narastających obrzęków kończyn dolnych, utrzymujących się od 5 dni. W badaniu przedmiotowym: trzeszczenia u podstawy obu płuc, obrzęki podudzi. Rozpoczęto leczenie diuretyczne.',
    symptoms: ['duszność wysiłkowa', 'obrzęki kończyn dolnych', 'osłabienie'],
  },
  {
    id: 'note-002',
    patientId: 'pat-001',
    encounterId: 'enc-001',
    authorId: 'stf-006',
    createdAt: daysAgo(2),
    category: 'nursing',
    title: 'Obserwacja pielęgniarska',
    content:
      'Pacjent w stanie ogólnym dobrym. Bilans płynów dodatni, kontynuacja leczenia moczopędnego. Masa ciała spadła o 1,2 kg w ciągu doby.',
  },
  {
    id: 'note-003',
    patientId: 'pat-001',
    encounterId: 'enc-002',
    authorId: 'stf-002',
    createdAt: daysAgo(2),
    category: 'consultation',
    title: 'Konsultacja kardiologiczna',
    content:
      'Zalecono zwiększenie dawki furosemidu oraz kontrolę elektrolitów co 48h. Echo serca planowane w ciągu tygodnia.',
  },

  {
    id: 'note-004',
    patientId: 'pat-002',
    encounterId: 'enc-004',
    authorId: 'stf-002',
    createdAt: daysAgo(1),
    category: 'admission',
    title: 'Przyjęcie - podejrzenie ACS',
    content:
      'Pacjentka zgłosiła się z bólem zamostkowym o charakterze uciskowym, promieniującym do lewej ręki, trwającym ok. 40 minut. W EKG uniesienie odcinka ST w odprowadzeniach II, III, aVF. Wykonano pilną koronarografię.',
    symptoms: ['ból w klatce piersiowej', 'duszność', 'poty'],
  },
  {
    id: 'note-005',
    patientId: 'pat-002',
    encounterId: 'enc-005',
    authorId: 'stf-002',
    createdAt: hoursAgo(19),
    category: 'progress',
    title: 'Stan po koronarografii',
    content:
      'Zabieg przebiegł bez powikłań. Implantowano stent do gałęzi okalającej. Pacjentka w stanie stabilnym, bóle wieńcowe ustąpiły.',
  },

  {
    id: 'note-006',
    patientId: 'pat-004',
    encounterId: 'enc-007',
    authorId: 'stf-004',
    createdAt: daysAgo(5),
    category: 'admission',
    title: 'Przyjęcie - udar niedokrwienny',
    content:
      'Pacjentka przywieziona przez zespół ratownictwa medycznego z niedowładem połowiczym prawostronnym i zaburzeniami mowy, wywiad ok. 2h. Wykonano TK głowy, włączono leczenie trombolityczne.',
    symptoms: ['niedowład połowiczy', 'zaburzenia mowy'],
  },
  {
    id: 'note-007',
    patientId: 'pat-004',
    encounterId: 'enc-008',
    authorId: 'stf-004',
    createdAt: daysAgo(4),
    category: 'consultation',
    title: 'Ocena rehabilitacyjna',
    content:
      'Rozpoczęto wczesną rehabilitację ruchową. Siła mięśniowa kończyny górnej prawej 3/5, dolnej 4/5. Kontynuacja ćwiczeń.',
  },

  {
    id: 'note-008',
    patientId: 'pat-005',
    encounterId: 'enc-009',
    authorId: 'stf-001',
    createdAt: daysAgo(10),
    category: 'discharge',
    title: 'Wypis ze szpitala',
    content:
      'Pacjent w stanie ogólnym dobrym, ustąpiły objawy zapalenia płuc. Zakończono antybiotykoterapię dożylną. Zalecono kontrolę w poradni POZ za 2 tygodnie.',
  },

  {
    id: 'note-009',
    patientId: 'pat-007',
    encounterId: 'enc-010',
    authorId: 'stf-005',
    createdAt: hoursAgo(6),
    category: 'admission',
    title: 'Przyjęcie na SOR - uraz wielonarządowy',
    content:
      'Pacjent po wypadku komunikacyjnym, przywieziony przez zespół LPR. Triage czerwony. Wykonano TK całego ciała, stwierdzono uraz głowy i złamanie żeber.',
    symptoms: ['utrata przytomności', 'ból głowy', 'ból klatki piersiowej'],
  },

  {
    id: 'note-010',
    patientId: 'pat-009',
    encounterId: 'enc-012',
    authorId: 'stf-003',
    createdAt: daysAgo(2),
    category: 'admission',
    title: 'Przyjęcie planowe',
    content:
      'Pacjent przyjęty planowo celem przygotowania do cholecystektomii laparoskopowej z powodu objawowej kamicy żółciowej.',
  },

  {
    id: 'note-011',
    patientId: 'pat-012',
    encounterId: 'enc-014',
    authorId: 'stf-001',
    createdAt: daysAgo(7),
    category: 'admission',
    title: 'Przyjęcie - przeniesienie',
    content:
      'Pacjentka przeniesiona z innego ośrodka z powodu zaburzeń elektrolitowych w przebiegu przewlekłej choroby nerek. Stan ogólny średni.',
    symptoms: ['osłabienie', 'nudności'],
  },
  {
    id: 'note-012',
    patientId: 'pat-012',
    encounterId: 'enc-014',
    authorId: 'stf-006',
    createdAt: daysAgo(6),
    category: 'nursing',
    title: 'Obserwacja pielęgniarska',
    content:
      'Pacjentka wymaga pomocy przy czynnościach dnia codziennego. Ryzyko upadku - wysokie, zastosowano zabezpieczenia łóżka.',
  },

  {
    id: 'note-013',
    patientId: 'pat-015',
    encounterId: 'enc-016',
    authorId: 'stf-003',
    createdAt: daysAgo(1),
    category: 'discharge',
    title: 'Wypis po appendektomii',
    content:
      'Zabieg appendektomii laparoskopowej przebiegł bez powikłań. Pacjent w stanie dobrym, rana goi się prawidłowo. Zalecono kontrolę w poradni chirurgicznej za 2 tygodnie.',
  },
];
