import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Drawer } from 'primeng/drawer';
import { BadgeDirective } from 'primeng/badge';
import { LayoutStateService } from '../layout-state.service';
import { NAV_GROUPS, type NavItem } from '../nav-items';
import { PatientContextService } from '../../services/patient-context.service';
import { TeamMessageService } from '../../services/team-message.service';
import { FullNamePipe } from '../../pipes/full-name.pipe';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive, Drawer, BadgeDirective, NgTemplateOutlet, FullNamePipe],
  templateUrl: './app-sidebar.html',
  styleUrl: './app-sidebar.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'data-component-id': 'app-sidebar' },
})
export class AppSidebar {
  protected readonly layout = inject(LayoutStateService);
  protected readonly ctx = inject(PatientContextService);
  protected readonly teamMessageService = inject(TeamMessageService);

  protected readonly navGroups = NAV_GROUPS;
  protected readonly unreadCount = this.teamMessageService.unreadCount;

  protected linkFor(item: NavItem): unknown[] {
    if (item.patientScoped) {
      const id = this.ctx.patientId();
      return id ? ['/patients', id, item.patientScoped] : ['/select-patient'];
    }
    return [item.route ?? '/'];
  }

  protected queryParamsFor(item: NavItem): Record<string, string> | undefined {
    if (item.patientScoped && !this.ctx.patientId()) {
      return { next: item.patientScoped };
    }
    return undefined;
  }

  protected isMessagesItem(item: NavItem): boolean {
    return item.route === '/messages';
  }

  protected closeDrawer(): void {
    this.layout.closeMobileDrawer();
  }
}
