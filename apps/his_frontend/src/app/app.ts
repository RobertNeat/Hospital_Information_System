import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { Toast } from 'primeng/toast';

/**
 * Root component. Hosts the global `<p-toast>`/`<p-confirmdialog>` here rather
 * than in AppShell (see app-shell.ts) so toasts fired by resolvers/guards during
 * navigation -- before AppShell has mounted -- are never lost.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Toast, ConfirmDialog],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'app-root' },
})
export class App {}
