import type { Ward } from '../models';

export const WARDS: Ward[] = [
  { id: 'ward-int', name: 'Oddział Chorób Wewnętrznych', shortName: 'INT', floor: '2', beds: 30 },
  { id: 'ward-kar', name: 'Kardiologia', shortName: 'KAR', floor: '3', beds: 24 },
  { id: 'ward-chg', name: 'Chirurgia Ogólna', shortName: 'CHG', floor: '1', beds: 26 },
  { id: 'ward-neu', name: 'Neurologia', shortName: 'NEU', floor: '4', beds: 20 },
  { id: 'ward-sor', name: 'Szpitalny Oddział Ratunkowy', shortName: 'SOR', floor: '0', beds: 12 },
  { id: 'ward-amb', name: 'Poradnia Ogólna', shortName: 'AMB', floor: '0', beds: 0 },
];
