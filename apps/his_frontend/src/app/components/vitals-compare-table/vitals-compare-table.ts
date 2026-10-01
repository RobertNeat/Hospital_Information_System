import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Tag } from 'primeng/tag';
import type { TagSeverity, VitalSigns, VitalThreshold, VitalType } from '../../models';
import { VitalsService } from '../../services/vitals.service';

export interface VitalsCompareRow {
  type: VitalType;
  label: string;
  unit: string;
  valueA?: number;
  valueB?: number;
  delta?: number;
  arrow: 'up' | 'down' | 'flat' | 'none';
  arrowSeverity: TagSeverity;
}

/**
 * Distance outside the normal (low/high) band for a value, 0 if within it.
 * Used instead of a naive "higher is better/worse per parameter" rule, because that
 * rule is wrong in the below-normal direction: e.g. a hypotensive patient's systolic
 * rising from 75 to 100 mmHg is moving *toward* normal, which is clinically good, not
 * "rising BP is bad". Comparing distance-to-normal-band before/after gets this right
 * for every vital, including SpO2, with no per-parameter special case.
 */
function distanceOutsideBand(t: VitalThreshold, value: number): number {
  if (value < t.low) return t.low - value;
  if (value > t.high) return value - t.high;
  return 0;
}

/** Rounds to 1 decimal for temperature (which is entered with 1 decimal), 0 for the rest. */
function roundDelta(type: VitalType, delta: number): number {
  const factor = type === 'temperature' ? 10 : 1;
  return Math.round(delta * factor) / factor;
}

const FIELDS: VitalType[] = [
  'systolic',
  'diastolic',
  'heartRate',
  'temperature',
  'spo2',
  'respiratoryRate',
];

/**
 * Compares two `VitalSigns` measurements (A = earlier/baseline, B = later/current) parameter
 * by parameter, with a direction arrow colored by clinical meaning per parameter (e.g. a rising
 * temperature is colored danger, a rising SpO2 is colored success).
 */
@Component({
  selector: 'app-vitals-compare-table',
  imports: [Tag],
  templateUrl: './vitals-compare-table.html',
  styleUrl: './vitals-compare-table.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'vitals-compare-table' },
})
export class VitalsCompareTable {
  private readonly vitalsService = inject(VitalsService);

  readonly measurementA = input<VitalSigns | undefined>(undefined);
  readonly measurementB = input<VitalSigns | undefined>(undefined);

  protected readonly rows = computed<VitalsCompareRow[]>(() => {
    const a = this.measurementA();
    const b = this.measurementB();
    const thresholds = this.vitalsService.thresholds();
    return FIELDS.flatMap((type) => {
      const t = thresholds[type];
      return t ? [this.buildRow(type, t, a, b)] : [];
    });
  });

  constructor() {
    this.vitalsService.loadThresholds().subscribe({ error: () => undefined });
  }

  private buildRow(
    type: VitalType,
    t: VitalThreshold,
    a: VitalSigns | undefined,
    b: VitalSigns | undefined,
  ): VitalsCompareRow {
    const valueA = a?.[type];
    const valueB = b?.[type];
    const rawDelta = valueA !== undefined && valueB !== undefined ? valueB - valueA : undefined;
    const delta = rawDelta !== undefined ? roundDelta(type, rawDelta) : undefined;

    let arrow: VitalsCompareRow['arrow'] = 'none';
    let arrowSeverity: VitalsCompareRow['arrowSeverity'] = 'secondary';

    if (delta !== undefined) {
      if (delta === 0) {
        arrow = 'flat';
        arrowSeverity = 'secondary';
      } else {
        arrow = delta > 0 ? 'up' : 'down';
        const distA = valueA !== undefined ? distanceOutsideBand(t, valueA) : 0;
        const distB = valueB !== undefined ? distanceOutsideBand(t, valueB) : 0;
        arrowSeverity = distB < distA ? 'success' : distB > distA ? 'danger' : 'secondary';
      }
    }

    return {
      type,
      label: t.label,
      unit: t.unit,
      valueA,
      valueB,
      delta,
      arrow,
      arrowSeverity,
    };
  }
}
