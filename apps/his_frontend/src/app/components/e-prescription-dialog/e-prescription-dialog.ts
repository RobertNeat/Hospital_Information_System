import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ViewEncapsulation,
  input,
  output,
} from '@angular/core';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import type { Patient, Prescription, StaffMember } from '../../models';

/**
 * `ViewEncapsulation.None` + the globally-scoped `.his-erx-print` class below are
 * deliberate: `window.print()` prints the whole document, and component-scoped CSS
 * (view-encapsulated) cannot reach outside this component to hide the app shell during
 * print. This is the one component in the app that needs a global print stylesheet;
 * everything else stays view-encapsulated as usual.
 */
@Component({
  selector: 'app-e-prescription-dialog',
  imports: [Dialog, Button, DatePipe],
  templateUrl: './e-prescription-dialog.html',
  styleUrl: './e-prescription-dialog.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class EPrescriptionDialog {
  readonly visible = input.required<boolean>();
  readonly prescription = input.required<Prescription>();
  readonly patient = input.required<Patient>();
  readonly prescriber = input.required<StaffMember>();

  readonly closed = output<void>();

  protected print(): void {
    window.print();
  }

  protected close(): void {
    this.closed.emit();
  }
}
