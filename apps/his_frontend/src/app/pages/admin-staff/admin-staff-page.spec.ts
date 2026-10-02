import { describe, expect, it, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ConfirmationService, MessageService } from 'primeng/api';
import { AuthService } from '../../services/auth.service';
import { staffServiceStub, TEST_USER } from '../../testing/staff-service.stub';
import { AdminStaffPage } from './admin-staff-page';

describe('AdminStaffPage', () => {
  let permissions: string[];

  beforeEach(() => {
    permissions = ['account:manage'];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: { hasPermission: (p: string) => permissions.includes(p) },
        },
        staffServiceStub,
        MessageService,
        ConfirmationService,
      ],
    });
  });

  it('renders the staff list', async () => {
    const fixture = TestBed.createComponent(AdminStaffPage);
    await fixture.whenStable();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Pracownicy');
    expect(text).toContain(TEST_USER.lastName);
  });

  it('hides activate/lock actions without account:manage', async () => {
    permissions = [];
    const fixture = TestBed.createComponent(AdminStaffPage);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    expect(page['canActivate'](TEST_USER)).toBe(false);
    expect(page['canLock'](TEST_USER)).toBe(false);
  });

  it('offers lock only for active accounts other than the current user', async () => {
    const fixture = TestBed.createComponent(AdminStaffPage);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    // Current user: active account, but locking one's own account is rejected by the backend.
    expect(page['canLock'](TEST_USER)).toBe(false);
    expect(page['canActivate'](TEST_USER)).toBe(false);

    const other = { ...TEST_USER, id: 'stf-other', accountStatus: 'active' as const };
    expect(page['canLock'](other)).toBe(true);
    expect(page['canActivate'](other)).toBe(false);

    const locked = { ...TEST_USER, id: 'stf-locked', accountStatus: 'locked' as const };
    expect(page['canLock'](locked)).toBe(false);
    expect(page['canActivate'](locked)).toBe(true);
  });

  it('hides both actions when the staff member has no account yet', async () => {
    const fixture = TestBed.createComponent(AdminStaffPage);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    const noAccount = { ...TEST_USER, id: 'stf-no-account', accountStatus: undefined };
    expect(page['canActivate'](noAccount)).toBe(false);
    expect(page['canLock'](noAccount)).toBe(false);
  });
});
