import type { ImagingModality, ScheduleSlot } from '../models';
import { seeded, seededRange } from './mock-utils';

const ROOMS_BY_MODALITY: Record<ImagingModality, string[]> = {
  USG: ['USG-1', 'USG-2'],
  RTG: ['RTG-1'],
  CT: ['TK-1'],
  MRI: ['RM-1'],
  MMG: ['MMG-1'],
  ENDOSCOPY: ['ENDO-1'],
  COLONOSCOPY: ['ENDO-2'],
  ANGIOGRAPHY: ['ANGIO-1'],
};

/** Deterministically generates 8:00-16:00 half-hour slots for `modality` on `date` (YYYY-MM-DD). */
export function generateSlots(modality: ImagingModality, date: string): ScheduleSlot[] {
  const rooms = ROOMS_BY_MODALITY[modality];
  const seed = hashString(`${modality}-${date}`);
  const rng = seeded(seed);
  const slots: ScheduleSlot[] = [];

  for (const room of rooms) {
    for (let hour = 8; hour < 16; hour++) {
      for (const minute of [0, 30]) {
        const start = new Date(`${date}T00:00:00`);
        start.setHours(hour, minute, 0, 0);
        const end = new Date(start);
        end.setMinutes(end.getMinutes() + 30);
        const available = seededRange(rng, 0, 1) > 0.35;
        slots.push({
          id: `slot-${modality}-${room}-${date}-${hour}${minute}`,
          modality,
          start: start.toISOString(),
          end: end.toISOString(),
          room,
          available,
        });
      }
    }
  }
  return slots;
}

function hashString(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) {
    h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  }
  return h;
}
