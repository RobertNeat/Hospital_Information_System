import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { StatusTag } from '../status-tag/status-tag';
import type { LabObservation, ResultFlag } from '../../models';

const FLAG_ICONS: Record<ResultFlag, string> = {
  N: '',
  L: 'pi pi-arrow-down',
  H: 'pi pi-arrow-up',
  LL: 'pi pi-angle-double-down',
  HH: 'pi pi-angle-double-up',
  A: 'pi pi-exclamation-triangle',
};

const FLAG_CLASS: Record<ResultFlag, string> = {
  N: 'his-lro__flag--n',
  L: 'his-lro__flag--l',
  H: 'his-lro__flag--h',
  LL: 'his-lro__flag--ll',
  HH: 'his-lro__flag--hh',
  A: 'his-lro__flag--a',
};

/**
 * Table of a lab result's individual analyte observations: value, unit, reference
 * range and flag, with H/L/HH/LL colour-coded and shown with a directional icon.
 * Used inside `app-data-table`'s row-expansion template on the lab results tab.
 */
@Component({
  selector: 'app-lab-result-observations',
  imports: [StatusTag],
  templateUrl: './lab-result-observations.html',
  styleUrl: './lab-result-observations.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'lab-result-observations' },
})
export class LabResultObservations {
  readonly observations = input.required<LabObservation[]>();

  protected flagIcon(flag: ResultFlag): string {
    return FLAG_ICONS[flag];
  }

  protected flagClass(flag: ResultFlag): string {
    return FLAG_CLASS[flag];
  }

  protected referenceRangeText(obs: LabObservation): string {
    const { low, high, text } = obs.referenceRange;
    if (text) return text;
    if (low !== undefined && high !== undefined) return `${low} - ${high}`;
    if (low !== undefined) return `> ${low}`;
    if (high !== undefined) return `< ${high}`;
    return '-';
  }
}
