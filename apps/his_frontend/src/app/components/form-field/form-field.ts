import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import type { AbstractControl } from '@angular/forms';
import { of, startWith, switchMap } from 'rxjs';
import {
  DEFAULT_VALIDATION_MESSAGE,
  VALIDATION_MESSAGES,
} from '../../constants/validation-messages';

@Component({
  selector: 'app-form-field',
  imports: [],
  templateUrl: './form-field.html',
  styleUrl: './form-field.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormField {
  readonly label = input.required<string>();
  readonly for = input<string>();
  readonly required = input(false);
  readonly control = input<AbstractControl | null>(null);
  readonly hint = input<string>();

  // Under zoneless + OnPush, calling markAllAsTouched()/updateValueAndValidity() on a
  // projected control only marks the *parent's* view dirty -- FormField itself never
  // gets a change-detection pass from that. Subscribing to the control's own `events`
  // stream (emitted on touched/status/value changes) gives this component its own
  // reactive trigger so the rendered error text actually updates.
  private readonly controlTick = toSignal(
    toObservable(this.control).pipe(
      switchMap((c) => (c ? c.events.pipe(startWith(null)) : of(null))),
    ),
    { initialValue: null },
  );

  protected readonly errorMessage = computed(() => {
    this.controlTick();
    const c = this.control();
    if (!c || !c.touched || !c.errors) return null;
    const [key, err] = Object.entries(c.errors)[0] ?? [];
    if (!key) return null;
    const fn = VALIDATION_MESSAGES[key];
    return fn ? fn(err) : DEFAULT_VALIDATION_MESSAGE;
  });
}
