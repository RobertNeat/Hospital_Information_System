import { inject } from '@angular/core';
import { Router } from '@angular/router';
import type { CanActivateFn } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** Requires a session; otherwise redirects to `/login?returnUrl=<requested url>`. */
export const authGuard: CanActivateFn = (_route, state) => {
  if (inject(AuthService).isAuthenticated()) return true;
  const hasReturn = state.url !== '/' && !state.url.startsWith('/login');
  return inject(Router).createUrlTree(['/login'], {
    queryParams: hasReturn ? { returnUrl: state.url } : {},
  });
};

/**
 * Requires at least one of the given permissions (from the JWT `authorities` claim, see
 * `CurrentUser.permissions`); otherwise redirects to `/dashboard` so a role without access
 * never reaches a page whose API calls the backend would reject with 403.
 * `authGuard` must run first (session is assumed); combine via `canActivate: [authGuard, permissionGuard(...)]`.
 */
export function permissionGuard(...anyOf: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    if (anyOf.some((p) => auth.hasPermission(p))) return true;
    return inject(Router).createUrlTree(['/dashboard']);
  };
}

/** For `/login` and `/register`: a signed-in user goes to the dashboard instead. */
export const guestGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthService).isAuthenticated() ? router.createUrlTree(['/dashboard']) : true;
};
