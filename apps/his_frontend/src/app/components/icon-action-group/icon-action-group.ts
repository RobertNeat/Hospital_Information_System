import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';

export interface IconAction {
  id: string;
  icon: string;
  label: string;
  severity?:
    'primary' | 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast' | undefined;
  disabled?: boolean;
}

@Component({
  selector: 'app-icon-action-group',
  imports: [Button, Tooltip],
  templateUrl: './icon-action-group.html',
  styleUrl: './icon-action-group.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'icon-action-group' },
})
export class IconActionGroup {
  readonly actions = input.required<IconAction[]>();
  readonly size = input<'small' | 'large'>('small');
  readonly showLabels = input(false);

  readonly action = output<string>();
}
