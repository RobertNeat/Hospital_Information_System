# HisFrontend

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.0.

## Authentication (JWT)

- Login: `POST /api/v1/auth/login` (`{ employeeId, password }`) via `AuthService` (`src/app/services/auth.service.ts`). State is exposed as signals: `currentUser`, `isAuthenticated`, `permissions`, `expiresAt`.
- By default the token and expiry are persisted to `localStorage`, so a page reload restores the session (`restoreSession`, called from an app initializer, re-validates it against `GET /auth/me`) instead of ending it; another tab logging out ends this tab's session too (`storage` event). Build with `HIS_PERSIST_SESSION=false` (see `scripts/build.mjs`) to keep the token in memory only, ending the session on every reload.
- `authInterceptor` adds `Authorization: Bearer <token>` to `/api/v1/*` requests (not to login/register). `errorInterceptor` maps HTTP errors to `ApiError` (RFC 9457 `ProblemDetail`) and, on 401, clears the session and redirects to `/login?returnUrl=...`.
- The session also ends automatically at `expiresAt` (TTL 8 h). Logout (header menu) calls `POST /auth/logout` best-effort and clears the state.
- Routes: `authGuard` protects the whole app shell, `guestGuard` protects `/login` and `/register`.
- New accounts (`/register`) are created as `pending`; an administrator must activate them before the first login.
- Demo accounts (login = password): `admin`, `user` (doctor), `doctor`, `nurse`, `lab-tech`, `radiologist`, `pharmacist`, `registrar`; seeded accounts `EMP-0001` ... `EMP-0010` / `HisDemo2026!`.
- Dev proxy: `proxy.conf.json` forwards `/api` and `/ws` to `http://localhost:10420` (his_backend); nginx does the same in the container. Start the backend first, then `pnpm start`.
- All domain services (patients, EHR, lab, imaging, prescriptions, vitals, messaging, dashboard) call his_backend over REST; realtime updates arrive over STOMP (`RealtimeService`). The register page loads wards from the public `GET /api/v1/auth/register/wards`.
- `src/app/mock-data/` is not a data source of the app: it feeds the `testing/` stubs, unit tests and the SQL generator (`pnpm export:mocks`, see `scripts/export-mocks/README.md`).
- The production build warns that the initial bundle exceeds the 500 kB budget (Angular, PrimeNG and Chart.js dominate it); the limit is left unchanged.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:10400/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
pnpm build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed. `pnpm build` wraps `ng build` to apply the `HIS_PERSIST_SESSION` environment variable (see the Authentication section above); calling `ng build` directly skips that and always persists the session.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
