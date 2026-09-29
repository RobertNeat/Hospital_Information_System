import { Pipe, type PipeTransform } from '@angular/core';
import type { ISODate } from '../models';
import { ageFromBirthDate } from '../utils/date-utils';

/** Formats a birth date as age, e.g. "54 l.". */
@Pipe({ name: 'age' })
export class AgePipe implements PipeTransform {
  transform(birthDate: ISODate | null | undefined): string {
    if (!birthDate) return '';
    return `${ageFromBirthDate(birthDate)} l.`;
  }
}
