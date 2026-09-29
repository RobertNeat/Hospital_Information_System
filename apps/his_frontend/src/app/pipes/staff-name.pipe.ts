import { Pipe, inject, type PipeTransform } from '@angular/core';
import type { ID } from '../models';
import { StaffService } from '../services/staff.service';

/** Resolves a staff id to a display name, e.g. `authorId | staffName` -> "lek. Anna Nowak". */
@Pipe({ name: 'staffName' })
export class StaffNamePipe implements PipeTransform {
  private readonly staffService = inject(StaffService);

  transform(staffId: ID | null | undefined): string {
    if (!staffId) return '';
    return this.staffService.nameOf(staffId);
  }
}
