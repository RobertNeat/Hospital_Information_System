import type { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL, AUTH_LOGIN_URL, AUTH_REGISTER_URL } from '../config/api.config';
import { AuthService } from '../services/auth.service';

/** Public endpoints that must never carry (or require) a token. */
export function isPublicAuthUrl(url: string): boolean {
  return url.startsWith(AUTH_LOGIN_URL) || url.startsWith(AUTH_REGISTER_URL);
}

/** Adds `Authorization: Bearer <token>` to `/api/` requests (except login/register). */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(`${API_BASE_URL}/`) || isPublicAuthUrl(req.url)) return next(req);
  if (req.headers.has('Authorization')) return next(req);
  const token = inject(AuthService).token;
  return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req);
};
