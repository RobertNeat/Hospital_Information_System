import { Pipe, type PipeTransform } from '@angular/core';
import type { Patient, PatientSummary, StaffMember } from '../models';

type NameSource = Patient | PatientSummary | StaffMember | null | undefined;

function hasTitle(source: NameSource): source is StaffMember {
  return !!source && 'title' in source;
}

/** Formats a patient or staff member as "Nazwisko Imię", with staff title prefixed, e.g. "lek. Kowalski Jan". */
@Pipe({ name: 'fullName' })
export class FullNamePipe implements PipeTransform {
  transform(source: NameSource): string {
    if (!source) return '';
    const base = `${source.lastName} ${source.firstName}`;
    return hasTitle(source) ? `${source.title} ${base}` : base;
  }
}
