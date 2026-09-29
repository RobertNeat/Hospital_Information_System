import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppHeader } from '../app-header/app-header';
import { AppSidebar } from '../app-sidebar/app-sidebar';

/**
 * Root shell: header on top, sidebar + <main> below. `<main>` is the scroll
 * container (not `window`) so the patient-chart sticky bar sticks correctly.
 *
 * `<p-toast>`/`<p-confirmdialog>` are hosted in the root `App` component instead
 * of here (deviation from plan section 7): the router activates AppShell only
 * after guards/resolvers on the navigation finish, so on a fresh/deep-linked load
 * of `/patients/:badId`, `patientResolver`'s error toast fires before AppShell --
 * and this component's `<p-toast>` -- exists. Hosting them in App (always mounted)
 * ensures the "Nie znaleziono pacjenta" toast is never lost.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, AppHeader, AppSidebar],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'app-shell' },
})
export class AppShell {}
