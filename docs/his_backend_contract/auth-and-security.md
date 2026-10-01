# Uwierzytelnianie, autoryzacja i bezpieczeństwo

Opis JWT, logowania, kont, ról/uprawnień i reguł dostępu w `his_backend`. Powrót: [README.md](README.md).

Źródła w kodzie: `security/SecurityConfig`, `JwtTokenService`, `JwtProperties`, `TokenClaims`, `HisJwtAuthenticationConverter`, `RolePermissions`, `ProblemSecurityHandlers`, `auth/*`, `staff/*`, `common/web/GlobalExceptionHandler`.

## JWT

| Element | Wartość |
| --- | --- |
| Algorytm | HS256 (symetryczny klucz `HIS_JWT_SECRET`) |
| Nagłówek żądań | `Authorization: Bearer <token>` |
| Klucz | `his.security.jwt.secret` <- `HIS_JWT_SECRET`, **wymagany, min. 32 bajty UTF-8**; brak/za krótki = backend nie startuje (`IllegalStateException`). Domyślny klucz istnieje tylko w profilu `dev` (`application-dev.properties`) |
| TTL | `HIS_JWT_TTL`, domyślnie `8h` (format `Duration` Springa) |
| Issuer (`iss`) | `HIS_JWT_ISSUER`, domyślnie `his-backend`; walidowany przy dekodowaniu |
| Walidacja | podpis HS256, `exp`, `iss` (`JwtValidators.createDefaultWithIssuer`) |
| Sesja | brak (stateless); serwer nie przechowuje tokenów ani listy unieważnionych |

### Claimy

| Claim | Typ | Znaczenie |
| --- | --- | --- |
| `iss` | string | `his.security.jwt.issuer` |
| `sub` | string (UUID) | `user_account.id` (id konta, **nie** pracownika) |
| `iat`, `exp` | liczba (sekundy) | wystawienie (obcięte do sekund) i wygaśnięcie |
| `staffId` | string (UUID) | `staff_member.id`; tożsamość aktora w całym API |
| `employeeId` | string | login pracownika |
| `role` | string | wartość na drucie `StaffRole` (`doctor`, `nurse`, ...) |
| `wardId` | string (UUID) | oddział pracownika |
| `authorities` | string[] | `ROLE_<ROLE>` + uprawnienia `zasob:akcja` roli (posortowane) |

Uprawnienia pochodzą wyłącznie z claima `authorities` (bez odczytu bazy przy żądaniu). Konsekwencje:

- Blokada konta, zmiana roli lub wyłączenie nie unieważniają już wydanych tokenów (brak odwołania); token działa do `exp`.
- Niepoprawne lub brakujące claimy (`role`, `staffId`, `wardId`, `sub` nie-UUID) -> 401 ("Niepoprawne claimy tokenu").
- Przeterminowany lub błędny nagłówek Bearer jest ignorowany na `/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/register/wards` i `/ws` (nie blokuje publicznych ścieżek).
- Hasła: BCrypt cost 10, hashe w bazie bez prefiksu `{bcrypt}`; hasło ma maks. 72 bajty UTF-8.

## Wywołania usług e-* (`/fhir/**`)

Osobny łańcuch `FhirSecurityConfig` (`@Order(1)`, `securityMatcher("/fhir/**")`) przed łańcuchem JWT: bezstanowy, bez CSRF/CORS, autoryzacja nagłówkiem `X-Service-Key` równym `his.fhir.service-key` (`HIS_FHIR_SERVICE_KEY`, porównanie w stałym czasie, pusty klucz = zawsze 401). Błąd 401 to `OperationOutcome`. Token JWT nie działa na `/fhir/**`. Rozwiązanie przejściowe (jeden współdzielony klucz dla wszystkich usług `e-*`); docelowo mTLS. Szczegóły: [rest-api-fhir.md](rest-api-fhir.md).

## Endpointy auth

Szczegóły ścieżek: [rest-api-auth-staff.md](rest-api-auth-staff.md). Typy: [data-types.md](data-types.md).

| Endpoint | Dostęp | Uwagi |
| --- | --- | --- |
| `POST /api/v1/auth/login` | publiczny | `LoginRequest` -> 200 `LoginResponse` (`accessToken`, `user`, `expiresAt`) |
| `POST /api/v1/auth/logout` | uwierzytelniony | 204; **bezstanowy** (nic nie unieważnia); klient usuwa token |
| `GET /api/v1/auth/me` | uwierzytelniony | `CurrentUser` = `StaffMember` + `permissions` (bez `ROLE_*`) |
| `POST /api/v1/auth/register` | publiczny | 201 `StaffRegistrationResponse`; konto `pending` |
| `GET /api/v1/auth/register/wards` | publiczny | 200 `PublicWardResponse[]` (`id`, `name`, `shortName`) |
| `POST /api/v1/staff/{staffId}/activate` | `account:manage` (admin) | ustawia `active`, zeruje licznik prób i blokadę czasową |
| `POST /api/v1/staff/{staffId}/lock` | `account:manage` (admin) | ustawia `locked`; własnego konta nie można zablokować (409) |

### Logowanie - kolejność sprawdzeń

Brak enumeracji kont: nieznany login i złe hasło dają identyczne 401 (nieznany login także zużywa czas BCrypt). Błędna próba jest zapisywana mimo wyjątku (`noRollbackFor`).

1. Nieznany `employeeId` -> 401 `UNAUTHENTICATED` ("Niepoprawny identyfikator lub haslo").
2. Aktywna blokada czasowa (`locked_until` w przyszłości) -> **403** "Konto jest tymczasowo zablokowane..." - jeszcze przed sprawdzeniem hasła.
3. Złe hasło (lub hasło > 72 bajty) -> 401; jeśli konto ma status `active`, rośnie licznik prób.
4. Poprawne hasło, ale status `pending` -> 403 "Konto oczekuje na aktywacje przez administratora"; `locked` -> 403 "Konto jest zablokowane".
5. Sukces: licznik i blokada czasowa zerowane, zapis `last_login_at`, wystawienie tokenu.

Blokada czasowa po kolejnych nieudanych próbach (tylko konta `active`):

| Parametr | Zmienna | Domyślnie |
| --- | --- | --- |
| Liczba prób | `HIS_LOCKOUT_MAX_ATTEMPTS` | 5 |
| Czas blokady | `HIS_LOCKOUT_DURATION` | `15m` |

W momencie nałożenia blokady licznik jest zerowany (po wygaśnięciu zaczyna od 0). Blokada czasowa jest niezależna od statusu `locked` (blokada administracyjna; zdejmuje ją tylko `activate`).

### Walidacja żądań auth

| Pole | Ograniczenia |
| --- | --- |
| `LoginRequest.employeeId` | niepuste, maks. 30 |
| `LoginRequest.password` | niepuste, maks. 200 (bez minimalnej długości - konta proste muszą się logować) |
| `StaffRegistrationRequest.employeeId` | `^[A-Za-z0-9-]{4,20}$` |
| `StaffRegistrationRequest.password` | 8-72 znaki (i maks. 72 bajty UTF-8, inaczej 422 `password`) |
| `pwz` | pusty lub dokładnie 7 cyfr |
| `wardId` | musi istnieć, inaczej 422 (`errors[].code="notFound"`) |

Duplikaty `employeeId`, `pwz` lub `email` (e-mail porównywany bez rozróżniania wielkości liter) -> 409 `CONFLICT` z wymienionymi polami w `detail`. Rejestracja tworzy `staff_member` i `user_account` w jednej transakcji.

## Konta demo

Migracja `db/changelog/demo/001-demo-accounts.sql` (changesety z `context:reference`) tworzy oddział `DEMO` i 8 kont w statusie `active`. Według komentarza w pliku **login = hasło**; hashe BCrypt (cost 10) są w repozytorium.

| Login (= hasło) | Rola |
| --- | --- |
| `admin` | admin |
| `user` | doctor |
| `doctor` | doctor |
| `nurse` | nurse |
| `lab-tech` | lab_technician |
| `radiologist` | radiologist |
| `pharmacist` | pharmacist |
| `registrar` | registrar |

Ryzyko: ponieważ changesety mają kontekst `reference`, ładują się **także na produkcji** (domyślne `HIS_LIQUIBASE_CONTEXTS=reference`). Hasła są trywialne i publiczne, a `admin` ma pełne uprawnienia administracyjne. Zalecenie z `.github/pipeline_docs/production_deployment.md`: po wdrożeniu zablokować konta (`POST /api/v1/staff/{id}/lock` jako admin) lub zmienić hasła; hosty w zaufanej sieci LAN.

Dodatkowo w kontekście `mock` (profil `dev`, `HIS_LIQUIBASE_CONTEXTS=reference,mock`) powstają konta `EMP-0001`…`EMP-0010` (status `active`, wspólny hash BCrypt). Według README generatora (`apps/his_frontend/scripts/export-mocks/README.md`) wspólne hasło to `HisDemo2026!`; z samego hasha nie da się tego zweryfikować.

## Role i uprawnienia

Uprawnienie ma postać `zasob:akcja`; każda rola dostaje dodatkowo `ROLE_<ROLE>`. Macierz jest w `security/RolePermissions` i trafia do tokenu (`authorities`) oraz do `CurrentUser.permissions`.

Role: `doctor` (D), `nurse` (N), `lab_technician` (L), `radiologist` (R), `pharmacist` (P), `registrar` (G), `admin` (A). `x` = rola ma uprawnienie.

| Uprawnienie | D | N | L | R | P | G | A | Gdzie używane |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `patient:read` | x | x | x | x | x | x | x | `GET /patients`, `/patients/{id}`, `POST /patients/duplicate-check` |
| `patient:write` | | | | | | x | x | `POST /patients`, `PATCH /patients/{id}` |
| `admission:read` | x | x | | | | x | x | `GET /patients/{id}/admissions` |
| `admission:admit` | x | | | | | x | x | `POST /patients/{id}/admissions` |
| `admission:discharge` | x | | | | | | x | `POST /patients/{id}/discharge` |
| `ehr:read` | x | x | | | | | x | `ehr-summary`, `encounters`, `episodes`, `clinical-notes`, oraz (jak `read-limited`) diagnozy/alergie/przeciwwskazania/leczenie |
| `ehr:read-limited` | | | x | x | x | | | `diagnoses`, `allergies`, `contraindications`, `treatments` |
| `ehr:note:write` | x | | | | | | | `POST clinical-notes` - wszystkie kategorie |
| `ehr:note:write-nursing` | | x | | | | | | `POST clinical-notes` - `nursing`, `observation` |
| `ehr:note:write-consultation` | | | | x | | | | `POST clinical-notes` - `consultation` |
| `ehr:diagnosis:write` | x | | | | | | | `POST diagnoses` |
| `ehr:allergy:write` | x | x | | | | | | `POST allergies` |
| `lab-order:read` | x | x | x | | | | x | `lab-orders`, `lab-tests`, `lab-panels` |
| `lab-order:create` | x | | | | | | | `POST /patients/{id}/lab-orders` |
| `lab-order:cancel` | x | | | | | | | `POST /lab-orders/{id}/cancel` |
| `lab-order:update-status` | | | x | | | | x | `POST /lab-orders/{id}/status` (dowolny dozwolony status) |
| `lab-order:collect-specimen` | | x | | | | | | to samo, ale tylko `specimen_collected` |
| `lab-result:read` | x | x | x | | | | x | wyniki lab, trendy, inbox |
| `lab-result:acknowledge` | x | | | | | | | `POST /lab-results/{id}/acknowledge` |
| `lab-result:write` | | | x | | | | | **brak endpointu** |
| `imaging-order:read` | x | | | x | | | x | `imaging-orders`, `imaging-exams`, `imaging-slots` |
| `imaging-order:create` | x | | | | | | | `POST /patients/{id}/imaging-orders` |
| `imaging-order:cancel` | x | | | | | | | `POST /imaging-orders/{id}/cancel` |
| `imaging-order:update-status` | | | | x | | | x | `POST /imaging-orders/{id}/status` |
| `imaging-result:read` | x | x | | x | | | x | wyniki obrazowe, inbox |
| `imaging-result:acknowledge` | x | | | | | | | `POST /imaging-results/{id}/acknowledge` |
| `imaging-result:write` | | | | x | | | | **brak endpointu** |
| `prescription:read` | x | x | | | x | | x | `prescriptions`, `active-medications` |
| `prescription:create` | x | | | | | | | `POST /patients/{id}/prescriptions` |
| `prescription:cancel` | x | | | | | | | `POST /prescriptions/{id}/cancel` |
| `drug:read` | x | x | | | x | | x | `GET /drugs`, `/drugs/{id}` |
| `drug-safety-check:run` | x | | | | | | | `POST /drug-safety-checks` |
| `vitals:read` | x | x | | | | | x | odczyty, `latest`, `ward-overview` |
| `vitals:write` | x | x | | | | | | `POST /patients/{id}/vitals` |
| `vital-threshold:read` | x | x | | | | | x | `GET /vital-thresholds` |
| `vital-threshold:write` | | | | | | | x | **brak endpointu** |
| `message:read` | x | x | x | x | x | x | x | wątki i wiadomości (odczyt) |
| `message:write` | x | x | x | x | x | x | x | tworzenie wątków, wysyłka, `read` |
| `task:read` | x | x | | | | | x | `GET /tasks`, `GET /handoff-notes` |
| `task:write` | x | x | | | | | | `POST /tasks`, `/tasks/{id}/status`, `POST /handoff-notes` |
| `alert:read` | x | x | x | x | | | x | `GET /alerts`, subskrypcja `/topic/alerts/{wardId}` |
| `alert:acknowledge` | x | x | | | | | | `POST /alerts/{id}/acknowledge` |
| `staff:read` | x | x | x | x | x | x | x | `GET /staff`, `/staff/{id}` |
| `staff:write` | | | | | | | x | **brak endpointu** |
| `ward:read` | x | x | x | x | x | x | x | `GET /wards` |
| `ward:write` | | | | | | | x | **brak endpointu** |
| `dashboard:read` | x | x | x | x | x | x | x | `GET /dashboard/stats` |
| `account:manage` | | | | | | | x | `POST /staff/{id}/activate`, `/lock` |

Uwagi:

- Autoryzacja jest rolą (RBAC); **nie ma ograniczenia po oddziale** poza subskrypcją `/topic/alerts/{wardId}` (patrz [realtime-stomp.md](realtime-stomp.md)).
- Endpointy bez `@PreAuthorize`, wystarczy uwierzytelnienie: `GET /auth/me`, `POST /auth/logout`, `GET /dictionaries/icd-10`, `GET /terminology/snomed/*`.
- `POST /clinical-notes`: wymagane któreś z `ehr:note:write*`; kategoria spoza zakresu roli -> 403 ("Rola nie moze zapisywac notatek w kategorii ..."). Zakresy sumują się, jeśli rola ma kilka uprawnień.
- `POST /lab-orders/{id}/status` dla pielęgniarki (`collect-specimen`): dozwolony wyłącznie `specimen_collected`, inny status -> 403.

## Reguły dostępu HTTP (`SecurityConfig`)

| Wzorzec | Reguła |
| --- | --- |
| `/api/v1/auth/login`, `/api/v1/auth/register`, `GET /api/v1/auth/register/wards` | `permitAll` |
| `/actuator/health/**` | `permitAll` |
| `/ws/**` | `permitAll` na poziomie HTTP (handshake); uwierzytelnienie w ramce STOMP CONNECT |
| `/api/**` | `authenticated` |
| każde inne żądanie | `denyAll` |
| dispatcher `ERROR` | `permitAll` |
| `/fhir/**` | osobny łańcuch `FhirSecurityConfig` (klucz usługowy), patrz wyżej |

Autoryzacja metod: `@EnableMethodSecurity` + `@PreAuthorize("hasAuthority('...')")` na kontrolerach (patrz tabela wyżej). Sesja `STATELESS`, form login / HTTP Basic / logout Springa wyłączone.

## 401 i 403

Zawsze `application/problem+json` w kształcie `ProblemDetail` ([conventions.md](conventions.md#błędy-problemdetail)):

| Sytuacja | Odpowiedź |
| --- | --- |
| Brak/zły/wygasły token (warstwa filtrów) | 401, `code=UNAUTHENTICATED`, nagłówek `WWW-Authenticate: Bearer`, `detail="Wymagane uwierzytelnienie"` (bez przyczyny błędu tokenu) |
| Brak uprawnienia (`@PreAuthorize`, warstwa filtrów) | 403, `code=FORBIDDEN`, `detail="Brak uprawnien do wykonania operacji"` |
| `AccessDeniedException` z MVC dla anonima | 401 (`UNAUTHENTICATED`), dla uwierzytelnionego 403 |
| Reguła domenowa (`ForbiddenException`) | 403 z konkretnym `detail` (np. nie-uczestnik wątku, zmiana statusu cudzego zadania, kategoria notatki) |
| Złe dane logowania | 401, `detail="Niepoprawny identyfikator lub haslo"` |

Brak powiązania sesji z pracownikiem (token bez rozpoznanego `staffId`) przy operacjach zapisu -> 403 "Brak powiazania sesji z pracownikiem".

## CORS i CSRF

| Mechanizm | Stan |
| --- | --- |
| CSRF | wyłączony (API bezstanowe, token w nagłówku) |
| CORS | **domyślnie wyłączony** (jedna origin przez nginx). `HIS_CORS_ALLOWED_ORIGINS` (lista po przecinku) włącza go dla podanych origin: metody `GET, POST, PUT, PATCH, DELETE, OPTIONS`, nagłówki `Authorization, Content-Type, Accept`, `max-age` 3600 s; bez `allowCredentials` |
| WebSocket origin | `HIS_WS_ALLOWED_ORIGINS` (patrz [realtime-stomp.md](realtime-stomp.md)) |

## Znane ograniczenia

- Brak odwoływania tokenów i brak odświeżania (brak refresh tokenu); po `exp` trzeba zalogować się ponownie.
- W kodzie nie ma ograniczania liczby żądań (rate limiting) poza blokadą kont.
- Konta demo z publicznymi hasłami w kontekście `reference` (patrz wyżej).
- Uprawnienia zapisu bez endpointów (`staff:write`, `ward:write`, `vital-threshold:write`, `lab-result:write`, `imaging-result:write`) są w tokenie, ale nic ich nie sprawdza.
