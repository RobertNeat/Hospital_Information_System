import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Button } from 'primeng/button';

@Component({
  selector: 'app-wizard-step-footer',
  imports: [Button],
  templateUrl: './wizard-step-footer.html',
  styleUrl: './wizard-step-footer.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WizardStepFooter {
  readonly first = input(false);
  readonly last = input(false);
  readonly nextLabel = input('Dalej');
  readonly submitLabel = input('Zatwierdź');
  readonly busy = input(false);

  readonly back = output<void>();
  readonly next = output<void>();
  readonly submitted = output<void>();
  readonly cancelled = output<void>();
}
