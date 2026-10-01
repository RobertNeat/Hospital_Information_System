# REST: auth, kadry, oddziały, dashboard

Endpointy `/auth/*`, `/staff`, `/wards`, `/dashboard/stats`. Powrót: [README.md](README.md) | indeks: [rest-api.md](rest-api.md). Zasady JWT i blokady: [auth-and-security.md](auth-and-security.md).

## Auth (`AuthController`, `/api/v1/auth`)

| Metoda | Ścieżka | Uprawnienie | Body | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| POST | `/auth/login` | publiczny | `LoginRequest` | `LoginResponse` | 200; 401 złe dane; 403 konto tymczasowo zablokowane / `pending` / `locked`; 422 walidacja |
| POST | `/auth/logout` | uwierz. | - | puste | 204 (bezstanowy; nic nie unieważnia) |
| GET | `/auth/me` | uwierz. | - | `CurrentUserResponse` (`CurrentUser`) | 200; 404 gdy pracownik z tokenu już nie istnieje |
| POST | `/auth/register` | publiczny | `StaffRegistrationRequest` | `StaffRegistrationResponse` | 201 (`accountStatus="pending"`); 409 duplikat `employeeId`/`pwz`/`email`; 422 (m.in. nieistniejący `wardId`, hasło > 72 bajty) |
| GET | `/auth/register/wards` | publiczny | - | `PublicWardResponse[]` (`id`, `name`, `shortName`; sort: `name`, `id`) | 200; lista oddziałów dla formularza rejestracji (bez `floor`/`beds`) |

`GET /auth/me` zwraca świeże dane pracownika z bazy (`permissions` wyliczone z roli bieżącej w bazie, nie z tokenu).

## Pracownicy (`StaffController`, `/api/v1/staff`)

| Metoda | Ścieżka | Uprawnienie | Parametry | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/staff` | `staff:read` | query: `role` (`StaffRole`), `wardId` (UUID) | `StaffMemberResponse[]` (bez paginacji) | 200; 422 zły enum/UUID |
| GET | `/staff/{staffId}` | `staff:read` | - | `StaffMemberResponse` | 200; 404 (także zły format UUID) |
| POST | `/staff/{staffId}/activate` | `account:manage` | - | `StaffMemberResponse` | 200; 404 (brak pracownika lub konta) |
| POST | `/staff/{staffId}/lock` | `account:manage` | - | `StaffMemberResponse` | 200; 404; 409 próba zablokowania własnego konta |

- Sortowanie `/staff`: nazwisko, imię, `id` rosnąco.
- `StaffMemberResponse.accountStatus` pochodzi z `user_account` (pomijane, gdy pracownik nie ma konta); `online` = ma otwartą sesję STOMP na tej instancji ([realtime-stomp.md](realtime-stomp.md#rejestr-obecności)).
- `activate` odblokowuje także konto `locked` i zeruje licznik prób oraz blokadę czasową. `lock` ustawia `locked`; wydane tokeny nadal działają do wygaśnięcia ([auth-and-security.md](auth-and-security.md#jwt)).

## Oddziały (`WardController`, `/api/v1/wards`)

| Metoda | Ścieżka | Uprawnienie | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- |
| GET | `/wards` | `ward:read` | `WardResponse[]` (sort: `name`, `id`) | 200 |

Brak endpointów zapisu oddziałów (`ward:write` bez endpointu).

## Dashboard (`DashboardController`)

| Metoda | Ścieżka | Uprawnienie | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- |
| GET | `/dashboard/stats` | `dashboard:read` (każda rola) | `DashboardStatsResponse` | 200; 403 gdy token nie ma powiązania z pracownikiem |

Definicje liczników (liczone w bazie przy każdym żądaniu):

| Pole | Definicja |
| --- | --- |
| `admittedPatients` | pacjenci ze statusem `admitted` |
| `newResults` | wyniki **laboratoryjne** bez `reviewedAt` (niepotwierdzone przez lekarza; wyniki obrazowe nie są liczone) |
| `criticalAlerts` | alerty `critical` niepotwierdzone przez bieżącego użytkownika (`@viewerScoped`) |
| `openTasks` | zadania `open` przypisane do bieżącego użytkownika (`@viewerScoped`) |
| `pendingOrders` | zlecenia laboratoryjne + obrazowe w statusie `ordered`, `scheduled`, `specimen_collected` lub `in_progress` |
| `vitalsAnomalies` | liczba pacjentów (wszystkie oddziały) z co najmniej jedną anomalią w ostatnim odczycie ([rest-api-vitals.md](rest-api-vitals.md)) |
