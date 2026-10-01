# Plan prac: his_backend (stan na 2026-10-01)

## Cel i założenia

Backend `apps/his_backend` (Spring Boot 4.1.1, Java 25, Maven) dostarcza dane UI `apps/his_frontend`. Kontrakt oczekiwany przez frontend: `docs/his_frontend_contract`; kontrakt wystawiany przez backend: `docs/his_backend_contract`.

- Liquibase definiuje struktury SQL. Terminologia SNOMED CT nie jest w SQL; dane odwołują się do niej przez SCTID, a treść serwuje terminology server (ECL).
- PostgreSQL w kontenerze Docker z wolumenem. Dane mock z frontendu trafiają do bazy skryptami Liquibase.
- Spring Security z JWT; wylogowanie = usunięcie tokenu po stronie frontendu albo jego wygaśnięcie.
- Ten sam układ compose lokalnie i na produkcji: his_backend, his_frontend, PostgreSQL, e-receipt, e-laboratory, e-imaging.

## Przyjęte ustalenia

| Obszar | Ustalenie |
| --- | --- |
| Testy integracyjne | Testcontainers (`postgres:17-alpine`) |
| Terminology server | Snowstorm Lite 2.7.0, profil compose `terminology`, import RF2 ręcznie, domyślnie wyłączony (`HIS_SNOWSTORM_ENABLED=false`) |
| Generator mocków TS -> SQL | vitest (`pnpm export:mocks`) |
| Uwierzytelnianie | JWT stateless HS256 (`spring-boot-starter-oauth2-resource-server`), token w pamięci frontendu |
| Konta | proste konta demo (login = hasło) także na produkcji; ryzyko słabych haseł w LAN zaakceptowane |
| Compose | `deploy/compose.yml` (kanoniczny) + `deploy/compose.dev.yml` (nakładka: `build:`, porty loopback) + `deploy/local.env` |
| Usługi e-* | tylko health (`/actuator/health`), bez symulatorów |
| Frontend | zrealizowana warstwa auth (AuthService, interceptory, guardy, login/register); pozostałe serwisy na mockach |

## Stan realizacji

| Moduł | Zakres | Stan |
| --- | --- | --- |
| Infrastruktura danych | JPA, Liquibase (49 tabel, `schema/001..009`), `ddl-auto=validate`, Testcontainers | gotowe |
| Dane | reference (`vital_threshold`, `icd10_code`), mock z generatora, konta demo | gotowe |
| Fundament wspólny | `ApiProblem`, `GlobalExceptionHandler`, `PageResponse`, `SortWhitelist`, `WireEnum`, audyt | gotowe |
| `auth`, `staff` | JWT, login/logout/me/register, aktywacja/blokada, oddziały, pracownicy | gotowe |
| `patient` | pacjenci, przyjęcia, wypisy, encounter | gotowe |
| `ehr` | notatki, diagnozy, alergie, leczenie, ICD-10, ehr-summary | gotowe |
| `catalog` | badania lab, panele, badania obrazowe, sloty, leki, progi vitals | gotowe |
| `lab`, `imaging` | zlecenia z maszyną stanów, wyniki, acknowledge, trendy, inbox, karta bezpieczeństwa, sloty | gotowe |
| `prescription` | recepty, active-medications, drug-safety-checks | gotowe |
| `vitals` | zapis, anomalie, ward-overview | gotowe |
| `messaging` | wątki, wiadomości, zadania, przekazania zmiany | gotowe |
| `alert`, `dashboard` | alerty ze zdarzeń, acknowledge per użytkownik, statystyki | gotowe |
| `realtime` | STOMP `/ws` (JWT w CONNECT, push po commit, rejestr online) | gotowe |
| `terminology` | klient Snowstorm Lite, `/api/v1/terminology/snomed/*`; sprawdzony na lokalnym Lite (import 20261001) | gotowe |
| Wdrożenie | ujednolicony compose z e-*, Snowstorm Lite, proxy nginx `/api` i `/ws`, Actuator + health we wszystkich 4 aplikacjach Spring, `curl` w `spring.Dockerfile`, `validate_projects.sh` | gotowe |
| Frontend | warstwa auth | gotowe |
| Dokumentacja | `docs/his_frontend_contract`, `docs/his_backend_contract` | gotowe |
| Weryfikacja | lint, typecheck, prettier, testy frontu oraz `verify` we wszystkich 4 aplikacjach Spring zielone | gotowe |

### Pozostało

1. **Podmiana serwisów mockowych frontendu na HttpClient** (około 70 metod w 13 serwisach, ~1,6 tys. linii + przepisanie specyfikacji na `HttpTestingController`). Podział: patient/ward/staff/ehr; lab-order/lab-result/imaging-order/imaging-result; drug/prescription/vitals; team-message/dashboard + klient STOMP. `mock-data/` pozostaje (korzysta z niego generator).
   - Klient STOMP wymaga zgody na `@stomp/stompjs` (nowa biblioteka, CLAUDE.md pkt 3).
   - Po zalogowaniu na prawdziwe konto widoki oparte na ID z mocków (`stf-001` itd.) mogą być puste do czasu podmiany serwisów.
2. **Symulatory e-receipt / e-laboratory / e-imaging** (dziś tylko health). Wpięcie: `PrescriptionIssued` -> e-receipt (`eRxKey`); e-laboratory i e-imaging wołają `LabResultRecordingService.recordResult` / `ImagingResultRecordingService.recordResult`. Backend nie ma endpointów POST wyniku lab/imaging.
3. **mTLS** między his_backend a e-*: profil `mtls` istnieje, ale `client-auth=need` uniemożliwia zwykły healthcheck; wymaga certyfikatów i osobnego portu zarządzania.
4. **Weryfikacja bezpieczeństwa w CI:** `trivy fs`, `semgrep` i `validate_projects.sh` (wymaga `jq`) nie były uruchamiane lokalnie; sprawdzić w pierwszym przebiegu CI (hasła demo, placeholdery `change-me`, hashe BCrypt w SQL mogą zostać oflagowane).

## Ograniczenia i znane odstępstwa

Pełna lista odstępstw backend <-> `his_frontend_contract`: `docs/his_backend_contract/README.md`.

| Obszar | Stan faktyczny |
| --- | --- |
| `drug-safety-check:run` | tylko `doctor` (kontrakt §12: także nurse/pharmacist/admin "R") |
| Zadania | status zmienia osoba przypisana lub twórca; `admin` ma tylko `task:read`; `open -> done` dozwolone |
| `ehr:read-limited` | laborant, radiolog, farmaceuta: tylko diagnozy, alergie, przeciwwskazania, leczenia |
| Nieznany kod w body (lab `testCode`, imaging `examCode`) | 422, nie 404 |
| `acknowledge` wyników lab/imaging | `version` ignorowane (brak kolumny) |
| `DischargePatientRequest.version` | porównywane z wersją aktywnego `Admission`, nie pacjenta |
| Wygasanie recept | wyliczane przy odczycie; brak schedulera i zdarzeń |
| STOMP | token JWT sprawdzany tylko przy CONNECT; rejestr "online" per instancja backendu |
| Uprawnienia bez egzekwowania | `staff:write`, `ward:write`, `vital-threshold:write`, `lab-result:write`, `imaging-result:write` są w tokenie, ale żaden kontroler ich nie sprawdza |
| Konta demo | `db/changelog/demo/` ma kontekst `reference`, więc konta ładują się zawsze, także na produkcji (istnieją tylko konteksty `reference` i `mock`) |
| Zdarzenia bez konsumenta | `PatientAdmitted`, `PatientDischarged`, trzy zdarzenia EHR, dwa zdarzenia recept |
| Niezweryfikowane uruchomieniem | czy `anyRequest().denyAll()` daje 401 czy 403 poza `/api/**`, `/ws/**`, `/actuator/health/**` |

## Operacje na środowisku

- **Serwer produkcyjny (192.168.1.160):** w `config.env` ustawić `HIS_DB_NAME`, `HIS_DB_USER`, `HIS_DB_PASSWORD`, `HIS_JWT_SECRET` (min. 32 bajty, wymagane); opcjonalnie `HIS_JWT_TTL`, `HIS_JWT_ISSUER`, `HIS_LOCKOUT_*`, `HIS_CORS_ALLOWED_ORIGINS`, `HIS_WS_ALLOWED_ORIGINS`, `HIS_LIQUIBASE_CONTEXTS`, `COMPOSE_PROFILES=terminology`, `HIS_SNOWSTORM_*`. Zmienić hasła kont demo po pierwszym wdrożeniu.
- Wolumeny stosu: `hospital-information-system_postgres-data`, `hospital-information-system_snowstorm-lite-data`; indeks Snowstorm wymaga importu RF2.
- Jeśli porty 5432/8080 są zajęte, ustawić `HIS_DB_HOST_PORT` / `HIS_SNOWSTORM_HOST_PORT` przed `pnpm stack:up`.

## Komendy

| Scenariusz | Komenda |
| --- | --- |
| Pełny stos lokalnie | `docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env up -d --build` (`pnpm stack:up`; ze Snowstormem `--profile terminology`) |
| Tryb IDE | `pnpm stack:db` (sam postgres), potem `pnpm start:backend` (profil `dev`: kontekst `reference,mock`) i `pnpm start` (proxy -> 10420). Nie uruchamiać kontenera `his-backend` (konflikt portu 10420) |
| Weryfikacja | `pnpm lint`, `pnpm typecheck`, `pnpm format:check`, `pnpm test:frontend`, `cd apps/his_backend; .\mvnw.cmd verify` (wymaga Dockera dla Testcontainers); to samo `verify` w `apps/e-receipt`, `apps/e-laboratory`, `apps/e-imaging` |
| Regeneracja mocków | `pnpm export:mocks` (wynik: `apps/his_backend/src/main/resources/db/changelog/{reference,mock}`; wykonanych changesetów schematu nie edytować) |

Logowanie: konta demo `admin/admin`, `user/user` (lekarz), `doctor`, `nurse`, `lab-tech`, `radiologist`, `pharmacist`, `registrar` (hasło = login); konta mocka `EMP-0001..EMP-0010` / `HisDemo2026!`.

## Zasady pracy (CLAUDE.md)

1. Bez commitów; kod zostaje do przeglądu.
2. Na koniec: lint, typecheck, prettier, testy; błędy naprawiać wąsko wyspecjalizowanymi agentami.
3. Tylko dojrzałe biblioteki; nową lub mało znaną zgłosić użytkownikowi i poczekać na akceptację.
4. Nie uruchamiać drugiej instancji działającego serwisu; uruchomione przez siebie zatrzymać i zwolnić porty.

## Odnośniki

- `docs/his_frontend_contract/` - oczekiwania frontendu (ERD, API, konwencje).
- `docs/his_backend_contract/` - oferta backendu (REST, auth, STOMP, zdarzenia, konfiguracja).
- `apps/his_frontend/scripts/export-mocks/README.md` - generator danych mock i polityka zmian changesetów.
- `.github/pipeline_docs/production_deployment.md` - deploy, zmienne środowiskowe, Postgres, Snowstorm, konta demo.
- `README.md` (root) - uruchamianie lokalne i import RF2 do Snowstorm Lite.
