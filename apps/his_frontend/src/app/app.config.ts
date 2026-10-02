import { registerLocaleData } from '@angular/common';
import localePl from '@angular/common/locales/pl';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
  inject,
} from '@angular/core';
import {
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withRouterConfig,
} from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { firstValueFrom } from 'rxjs';
import { providePrimeNG } from 'primeng/config';
import { HisTitleStrategy } from './config/his-title-strategy';
import { PRIMENG_PL } from './config/primeng-pl';
import { HisPreset } from './config/theme-preset';
import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';
import { errorInterceptor } from './interceptors/error.interceptor';
import { AuthService } from './services/auth.service';
import { ThemeService } from './services/theme.service';

registerLocaleData(localePl);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withRouterConfig({ paramsInheritanceStrategy: 'always' }),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    providePrimeNG({
      theme: {
        preset: HisPreset,
        options: { darkModeSelector: '.app-dark', cssLayer: false },
      },
      translation: PRIMENG_PL,
      ripple: true,
    }),
    MessageService,
    ConfirmationService,
    { provide: LOCALE_ID, useValue: 'pl' },
    { provide: TitleStrategy, useClass: HisTitleStrategy },
    // Applies a persisted dark-mode preference to <html> before first paint --
    // ThemeService applies `.app-dark` as a constructor side effect, so it must be
    // eagerly instantiated here rather than lazily on first use in AppHeader.
    provideAppInitializer(() => {
      inject(ThemeService);
    }),
    // Restores a persisted session (if any) before the router's initial navigation,
    // so the (synchronous) auth/guest guards see the right isAuthenticated() on reload.
    provideAppInitializer(() => firstValueFrom(inject(AuthService).restoreSession())),
  ],
};
