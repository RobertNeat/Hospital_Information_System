import { beforeEach, describe, expect, it } from 'vitest';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import type { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { authGuard, guestGuard } from './auth.guard';

describe('auth guards', () => {
  const authenticated = signal(false);

  beforeEach(() => {
    authenticated.set(false);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAuthenticated: authenticated } },
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
});
