import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type SectionHeaderVariant = 'navy' | 'primary' | 'success' | 'warn' | 'danger' | 'info';

@Component({
  selector: 'app-section-header',
  imports: [],
  templateUrl: './section-header.html',
  styleUrl: './section-header.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'section-header' },
})
export class SectionHeader {
  readonly title = input.required<string>();
  readonly icon = input<string>();
  readonly variant = input<SectionHeaderVariant>('navy');
  readonly count = input<number>();
}
