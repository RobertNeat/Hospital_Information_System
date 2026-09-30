import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Avatar } from 'primeng/avatar';
import { BadgeDirective } from 'primeng/badge';
import { Menu } from 'primeng/menu';
import type { MenuItem } from 'primeng/api';
import { Popover } from 'primeng/popover';
import { Tooltip } from 'primeng/tooltip';
import { LayoutStateService } from '../layout-state.service';
import { PatientContextService } from '../../services/patient-context.service';
import { StaffService } from '../../services/staff.service';
import { TeamMessageService } from '../../services/team-message.service';
import { ThemeService } from '../../services/theme.service';
import { FullNamePipe } from '../../pipes/full-name.pipe';

@Component({
  selector: 'app-header',
  imports: [RouterLink, Avatar, BadgeDirective, Menu, Popover, Tooltip, FullNamePipe],
  templateUrl: './app-header.html',
  styleUrl: './app-header.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'app-header' },
})
export class AppHeader {
  protected readonly layout = inject(LayoutStateService);
  private readonly router = inject(Router);
  protected readonly ctx = inject(PatientContextService);
  protected readonly staffService = inject(StaffService);
  protected readonly teamMessageService = inject(TeamMessageService);
  // Eagerly injected (also in app.config.ts's provideAppInitializer) so the
  // toggle button always reflects/controls the live theme mode.
  protected readonly theme = inject(ThemeService);

  protected readonly currentUser = this.staffService.currentUser;
  protected readonly unacknowledgedAlertCount = this.teamMessageService.unacknowledgedAlertCount;

  protected readonly latestAlerts = computed(() =>
    this.teamMessageService
      .alerts()
      .filter((a) => !a.acknowledged)
      .slice(0, 5),
  );

  protected readonly userInitials = computed(() => {
    const u = this.currentUser();
    return `${u.firstName.charAt(0)}${u.lastName.charAt(0)}`.toUpperCase();
  });

  protected readonly userMenuItems: MenuItem[] = [
    {
      label: 'Wyloguj (demo)',
      icon: 'pi pi-sign-out',
      routerLink: '/login',
    },
  ];

  protected onHamburger(): void {
    if (window.innerWidth < 1024) {
      this.layout.openMobileDrawer();
    } else {
      this.layout.toggleCollapsed();
    }
  }

  protected clearPatientContext(): void {
    this.ctx.clear();
    // Patient-scoped pages are meaningless without context -- leave them.
    if (
      this.router.url.startsWith('/patients/') &&
      !this.router.url.startsWith('/patients/register')
    ) {
      void this.router.navigate(['/patients']);
    }
  }
}
