import { describe, expect, it, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AppHeader } from './app-header';
import { AuthService } from '../../services/auth.service';
import { realtimeServiceStub } from '../../testing/realtime-service.stub';

type HeaderInternals = {
  clearPatientContext: () => void;
  userMenuItems: { command?: () => void }[];
};

describe('AppHeader', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), realtimeServiceStub],
    });
  });

  function setup(url: string) {
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue(url);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(AppHeader);
    return { navigate, header: fixture.componentInstance as unknown as HeaderInternals };
  }

  it('navigates to the patient list when cleared on a patient-scoped route', () => {
    const { navigate, header } = setup('/patients/pat-001/orders');
    header.clearPatientContext();
    expect(navigate).toHaveBeenCalledWith(['/patients']);
  });

  it('stays put when cleared on a non-patient route', () => {
    const { navigate, header } = setup('/dashboard');
    header.clearPatientContext();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('the logout menu item signs out and redirects', () => {
    const { header } = setup('/dashboard');
    const logout = vi.spyOn(TestBed.inject(AuthService), 'logoutAndRedirect').mockReturnValue();
    header.userMenuItems[0].command?.();
    expect(logout).toHaveBeenCalled();
  });
});
