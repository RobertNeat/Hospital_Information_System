import { beforeEach, describe, expect, it, vi } from 'vitest';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import type { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { MessageService } from 'primeng/api';
import { AuthService } from '../services/auth.service';
import { authGuard, guestGuard, permissionGuard } from './auth.guard';

describe('auth guards', () => {
  const authenticated = signal(false);
  const hasPermission = vi.fn(() => false);
  const messageAdd = vi.fn();

  beforeEach(() => {
    authenticated.set(false);
    hasPermission.mockReset().mockReturnValue(false);
    messageAdd.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: authenticated, hasPermission } },
        { provide: MessageService, useValue: { add: messageAdd } },
      ],
    });
  });

  const run = (guard: typeof authGuard, url = '/patients/pat-1') =>
    TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
    );

  const serialize = (tree: unknown) => TestBed.inject(Router).serializeUrl(tree as UrlTree);

  it('authGuard lets an authenticated user through', () => {
    authenticated.set(true);
    expect(run(authGuard)).toBe(true);
  });

  it('authGuard redirects anonymous users to /login with returnUrl', () => {
    expect(serialize(run(authGuard))).toBe('/login?returnUrl=%2Fpatients%2Fpat-1');
  });

  it('authGuard omits returnUrl for the root', () => {
    expect(serialize(run(authGuard, '/'))).toBe('/login');
  });

  it('guestGuard allows anonymous users', () => {
    expect(run(guestGuard)).toBe(true);
  });

  it('guestGuard sends authenticated users to the dashboard', () => {
    authenticated.set(true);
    expect(serialize(run(guestGuard))).toBe('/dashboard');
  });

  it('permissionGuard lets a user with the permission through', () => {
    hasPermission.mockReturnValue(true);
    expect(run(permissionGuard('patient:write'))).toBe(true);
    expect(messageAdd).not.toHaveBeenCalled();
  });

  it('permissionGuard redirects to /dashboard and warns (deferred past bootstrap) when the permission is missing', () => {
    vi.useFakeTimers();
    try {
      const result = run(permissionGuard('patient:write'));
      expect(serialize(result)).toBe('/dashboard');
      // Deferred via setTimeout so the toast isn't emitted before <p-toast> mounts on a
      // hard page load (see comment in auth.guard.ts) -- not called synchronously.
      expect(messageAdd).not.toHaveBeenCalled();
      vi.runAllTimers();
      expect(messageAdd).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'warn', summary: 'Brak uprawnień' }),
      );
    } finally {
      vi.useRealTimers();
    }
  });
});
