import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

interface ChartNavItem {
  label: string;
  segment: string;
}

const ITEMS: ChartNavItem[] = [
  { label: 'Dane pacjenta', segment: 'overview' },
  { label: 'Historia choroby', segment: 'history' },
  { label: 'Wyniki badań', segment: 'results' },
  { label: 'Parametry życiowe', segment: 'vitals' },
  { label: 'Zlecenia', segment: 'orders' },
  { label: 'Leki i recepty', segment: 'prescriptions' },
];

@Component({
  selector: 'app-patient-chart-nav',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './patient-chart-nav.html',
  styleUrl: './patient-chart-nav.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'patient-chart-nav' },
})
export class PatientChartNav {
  readonly patientId = input.required<string>();

  protected readonly items = ITEMS;

  protected linkFor(item: ChartNavItem): unknown[] {
    return ['/patients', this.patientId(), item.segment];
  }
}
