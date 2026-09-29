import type { HandoffNote } from '../models';
import { daysAgo, hoursAgo } from './mock-utils';

export const HANDOFF_NOTES: HandoffNote[] = [
  {
    id: 'hon-001',
    wardId: 'ward-int',
    shiftDate: daysAgo(0).slice(0, 10),
    shift: 'day',
    fromId: 'stf-006',
    toId: 'stf-007',
    createdAt: hoursAgo(1),
    generalNotes:
      'Oddział pełny, brak wolnych łóżek. Zwrócić uwagę na pacjentów z ryzykiem upadku.',
    patientNotes: [
      {
        patientId: 'pat-001',
        situation: 'Pacjent z zaostrzeniem niewydolności serca, 3. doba hospitalizacji.',
        background: 'Wywiad nadciśnienia i cukrzycy typu 2, leczony ambulatoryjnie.',
        assessment:
          'Bilans płynów dodatni, obrzęki zmniejszają się po zwiększeniu dawki furosemidu.',
        recommendation: 'Kontynuować leczenie diuretyczne, kontrola elektrolitów rano.',
      },
      {
        patientId: 'pat-012',
        situation: 'Pacjentka z przewlekłą chorobą nerek, zaburzenia elektrolitowe.',
        background: 'Przeniesiona z innego ośrodka 7 dni temu, wysokie ryzyko upadku.',
        assessment: 'Stan stabilny po korekcie potasu, nadal wymaga częstej kontroli.',
        recommendation: 'Kontrola elektrolitów co 12h, zabezpieczenia przeciwupadkowe.',
      },
    ],
  },
  {
    id: 'hon-002',
    wardId: 'ward-kar',
    shiftDate: daysAgo(0).slice(0, 10),
    shift: 'night',
    fromId: 'stf-007',
    toId: 'stf-006',
    createdAt: hoursAgo(12),
    generalNotes: 'Jeden pacjent po zabiegu koronarografii, wymaga monitorowania miejsca wkłucia.',
    patientNotes: [
      {
        patientId: 'pat-002',
        situation: 'Pacjentka po angioplastyce ze stentem, 1. doba po zabiegu.',
        background: 'Przyjęta z ostrym zespołem wieńcowym, bez powikłań okołozabiegowych.',
        assessment: 'Parametry stabilne, miejsce wkłucia bez cech krwiaka.',
        recommendation: 'Kontrola parametrów co 4h, pionizacja zgodnie z zaleceniem lekarskim.',
      },
    ],
  },
  {
    id: 'hon-003',
    wardId: 'ward-sor',
    shiftDate: daysAgo(1).slice(0, 10),
    shift: 'night',
    fromId: 'stf-010',
    toId: 'stf-009',
    createdAt: daysAgo(1),
    generalNotes: 'Spokojny dyżur, jeden pacjent urazowy przekazany na oddział chirurgii.',
    patientNotes: [
      {
        patientId: 'pat-007',
        situation: 'Pacjent po wypadku komunikacyjnym, triage czerwony.',
        background: 'Uraz wielonarządowy, wykonano TK całego ciała.',
        assessment: 'Stan stabilny po wstępnym zaopatrzeniu, obserwacja neurologiczna.',
        recommendation: 'Kontynuować obserwację, kontrola TK głowy za 24h.',
      },
    ],
  },
];
