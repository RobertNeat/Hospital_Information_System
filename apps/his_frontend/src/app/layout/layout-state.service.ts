import { Injectable, computed, signal } from '@angular/core';

/**
 * Shared sidebar collapse/drawer state for AppShell / AppHeader / AppSidebar.
 * Lives under `layout/` (not `services/`) because it's a presentation-layer
 * concern of the shell, not part of Phase 0a's data/contract layer.
 */
@Injectable({ providedIn: 'root' })
export class LayoutStateService {
  private readonly _collapsed = signal(false);
  readonly collapsed = this._collapsed.asReadonly();

  private readonly _mobileDrawerOpen = signal(false);
  readonly mobileDrawerOpen = this._mobileDrawerOpen.asReadonly();

  readonly sidebarWidth = computed(() =>
    this._collapsed() ? 'var(--his-sidebar-w-collapsed)' : 'var(--his-sidebar-w)',
  );

  toggleCollapsed(): void {
    this._collapsed.update((v) => !v);
  }

  openMobileDrawer(): void {
    this._mobileDrawerOpen.set(true);
  }

  closeMobileDrawer(): void {
    this._mobileDrawerOpen.set(false);
  }
}
