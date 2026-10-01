# Plan prac: integracja HIS (stan na 2026-10-01)

## Cel i założenia

Architektura docelowa (README.md): `his_frontend` --(REST + STOMP)--> `his_backend` --(FHIR)--> `e-receipt`, `e-laboratory`, `e-imaging`; `his_backend` --> Snowstorm Lite 2.7.0 (SNOMED CT) i PostgreSQL.

- `his_frontend`: interfejs pracownika szpitala (wizyty pacjenta, e-recepty, zlecenia obrazowań i badań laboratoryjnych; lekarze różnych specjalizacji i personel). Docelowo bez danych mockowych w serwisach: wszystko z `his_backend`.
- `his_backend` (Spring Boot 4.1.1, Java 25, Maven): główny backend; logika domenowa, dane w PostgreSQL (struktury i dane początkowe: Liquibase), dobór klasyfikacji chorób/objawów/procedur wg specjalizacji lekarza. Kontrakt oczekiwany przez frontend: `docs/his_frontend_contract`; wystawiany: `docs/his_backend_contract`.
- SNOMED CT: SQL przechowuje wyłącznie SCTID; treść serwuje Snowstorm Lite (ECL). Dane licencjonowane nie są w danych początkowych.
- `e-receipt`, `e-laboratory`, `e-imaging`: proste serwisy naśladujące zewnętrzne systemy; każdy ma interfejs Thymeleaf (bez uwierzytelniania) do zmiany stanu zleceń; zmiana stanu wpływa na stan zleceń w HIS; łączność z his_backend przez FHIR z mTLS.
- Spring Security z JWT; wylogowanie = usunięcie tokenu po stronie frontendu albo jego wygaśnięcie.
- Ten sam układ compose lokalnie i na produkcji: his_backend, his_frontend, PostgreSQL, e-receipt, e-laboratory, e-imaging (+ Snowstorm Lite w profilu `terminology`).

## Przyjęte ustalenia

| Obszar | Ustalenie |
| --- | --- |
| Testy integracyjne | Testcontainers (`postgres:17-alpine`) |
| Biblioteka FHIR | HAPI FHIR 8.12.1 (`hapi-fhir-base` + `hapi-fhir-structures-r4`), działa ze Spring Boot 4.1.1 / Java 25; klient HTTP: Spring `RestClient`, treść jako `String` (bez `hapi-fhir-client`) |
| Autoryzacja e-* -> HIS | mTLS (domyślnie włączony; certyfikat klienta zaufany przez CA `HIS-CA`, dozwolone CN `HIS_FHIR_ALLOWED_CLIENT_CNS`); FHIR na osobnym porcie HTTPS (his_backend 10424), API/`/ws` na HTTP 10420; wyłączenie: `HIS_MTLS_ENABLED=false` |
| Terminology server | Snowstorm Lite 2.7.0, profil compose `terminology`, import RF2 ręcznie, domyślnie wyłączony (`HIS_SNOWSTORM_ENABLED=false`) |
| Generator mocków TS -> SQL | vitest (`pnpm export:mocks`) |
| Uwierzytelnianie | JWT stateless HS256 (`spring-boot-starter-oauth2-resource-server`), token w pamięci frontendu |
| Konta | proste konta demo (login = hasło) także na produkcji; ryzyko słabych haseł w LAN zaakceptowane |
| Compose | `deploy/compose.yml` (kanoniczny) + `deploy/compose.dev.yml` (nakładka: `build:`, porty loopback) + `deploy/local.env` |
| Usługi e-* | `e-receipt`, `e-laboratory`, `e-imaging`: symulatory (UI Thymeleaf + FHIR R4 HAPI, stan w pamięci, wywołanie zwrotne do HIS przez mTLS; FHIR na porcie HTTPS 10421-10423, UI Thymeleaf na osobnym porcie HTTP 10431-10433, health na porcie zarządzania 10441-10443) |
| Frontend | zrealizowana warstwa auth (AuthService, interceptory, guardy, login/register); wszystkie serwisy domenowe korzystają z his_backend (etapy poniżej) |

## Stan realizacji

| Moduł | Zakres | Stan |
| --- | --- | --- |
| Infrastruktura danych | JPA, Liquibase (49 tabel, `schema/001..010`), `ddl-auto=validate`, Testcontainers | gotowe |
| Dane | reference (`vital_threshold`, `icd10_code`), mock z generatora, konta demo | gotowe |
| Fundament wspólny | `ApiProblem`, `GlobalExceptionHandler`, `PageResponse`, `SortWhitelist`, `WireEnum`, audyt | gotowe |
| `auth`, `staff` | JWT, login/logout/me/register, publiczna lista oddziałów dla rejestracji (`GET /auth/register/wards`), aktywacja/blokada, oddziały, pracownicy | gotowe |
| `patient` | pacjenci, przyjęcia (`outpatient` bez oddziału/lekarza/powodu), wypisy, encounter; 409 przy duplikacie PESEL z `errors[]` | gotowe |
| `ehr` | notatki, diagnozy, alergie (z `GET` pojedynczych zasobów), leczenie, ICD-10, ehr-summary | gotowe |
| `catalog` | badania lab, panele, badania obrazowe, sloty, leki, progi vitals | gotowe |
| `lab`, `imaging` | zlecenia z maszyną stanów, wyniki, acknowledge, trendy, inbox, karta bezpieczeństwa, sloty | gotowe |
| `prescription` | recepty, active-medications, drug-safety-checks | gotowe |
| `vitals` | zapis, anomalie, ward-overview | gotowe |
| `messaging` | wątki, wiadomości, zadania, przekazania zmiany | gotowe |
| `alert`, `dashboard` | alerty ze zdarzeń, acknowledge per użytkownik, statystyki | gotowe |
| `realtime` | STOMP `/ws` (JWT w CONNECT, push po commit, rejestr online) | gotowe |
| `terminology` | klient Snowstorm Lite, `/api/v1/terminology/snomed/*`; sprawdzony na lokalnym Lite (import 20261001) | gotowe |
| Integracja `his_backend` <-> `e-receipt` | wysyłka recept `e_prescription` (AFTER_COMMIT, best effort) i zapis `eRxKey`; `PUT /fhir/MedicationRequest/{id}` zmienia stan recepty w HIS (mTLS); anulowanie w HIS przekazywane do e-receipt; docs: `rest-api-fhir.md` | gotowe |
| Integracja `his_backend` <-> `e-laboratory` | wysyłka zleceń lab (`ServiceRequest`, AFTER_COMMIT, best effort, `HIS_ELAB_ENABLED`), anulowanie z HIS przekazywane; `PUT /fhir/ServiceRequest/{id}` zmienia stan zlecenia wg maszyny stanów, `POST /fhir/DiagnosticReport` zapisuje wynik przez `LabResultRecordingService`; symulator `e-laboratory` (UI Thymeleaf: lista, zmiana stanu, formularz wyniku); docs: `rest-api-fhir.md` | gotowe |
| Integracja `his_backend` <-> `e-imaging` | wysyłka zleceń obrazowych (`ServiceRequest`, AFTER_COMMIT, best effort, `HIS_EIMG_ENABLED`, zdarzenie `ImagingOrderPlaced`), anulowanie z HIS przekazywane; `PUT /fhir/ServiceRequest/{id}` zmienia stan zlecenia wg maszyny stanów (anulowanie zwalnia slot), `POST /fhir/DiagnosticReport` zapisuje wynik przez `ImagingResultRecordingService` (idempotentnie, auto-`completed`); ścieżki wspólne z lab (`FhirOrderHandler` kieruje po id zlecenia / systemie kodu badania); symulator `e-imaging` (UI Thymeleaf: lista, zmiana stanu, formularz wyniku); docs: `rest-api-fhir.md` | gotowe |
| Wdrożenie | ujednolicony compose z e-*, Snowstorm Lite, proxy nginx `/api` i `/ws`, Actuator + health we wszystkich 4 aplikacjach Spring, `curl` w `spring.Dockerfile`, `validate_projects.sh`; mTLS FHIR domyślnie włączony (certyfikaty `.certs/` + `scripts/gen-certs.sh`, osobne porty HTTPS/HTTP/zarządzania, `X-Service-Key` usunięty) | gotowe |
| Frontend: warstwa auth | AuthService, interceptory, guardy, login/register | gotowe |
| Frontend: serwisy na `HttpClient` | `ward`, `staff`, `patient`, `ehr`, `patient-context`, `drug`, `prescription`, `lab-order`, `lab-result`, `imaging-order`, `imaging-result`, `vitals`, `team-message`, `dashboard` (etapy F0-F6) | gotowe |
| Frontend: serwisy na mockach | brak (`mock-data/` zostaje dla stubów w `testing/` i testów) | gotowe |
| Dokumentacja | `docs/his_frontend_contract`, `docs/his_backend_contract` | gotowe |
| Weryfikacja | lint, typecheck, prettier, testy frontu (458, dwa przebiegi) i build zielone; `verify` zielone: his_backend (1007 testów), e-receipt (31), e-laboratory (34), e-imaging (35); `docker compose config` zielone; smoke test pełnego stosu z mTLS (healthy, recepta/zlecenie lab/obrazowe -> e-* i zmiana stanu -> HIS) zielony. Build frontu ostrzega o budżecie initial 500 kB (849 kB; limit bez zmian) | gotowe |

### Integracja his_frontend <-> his_backend (etapy F0-F8 gotowe; smoke test na realnym backendzie w "Pozostało")

Zasady dla wszystkich etapów: źródłem prawdy jest `docs/his_backend_contract` (w tym sekcja odstępstw); backend jest autorytatywny, logika mockowa serwisów jest usuwana; `mock-data/` zostaje (używa go generator); po mutacji ponowne pobranie danych zamiast modyfikacji lokalnych tablic; `version` przekazywane przy aktualizacjach; zmiana modelu TS = aktualizacja `docs/his_frontend_contract` w tym samym przebiegu. Etapy wykonywane sekwencyjnie w jednym drzewie roboczym.

| Etap | Zakres | Stan |
| --- | --- | --- |
| F0 Fundament | stałe URL domen w `config/api.config.ts`, helper query/sort/`Page`, wzorzec specyfikacji na `HttpTestingController`, bazowy przebieg lint/typecheck/test | gotowe |
| F1 Słowniki | `ward`, `staff` (+ cache synchroniczny dla `nameOf` po zalogowaniu, `currentUser` z `AuthService` zamiast `stf-001`) | gotowe |
| F2 Pacjent i EHR | `patient`, `ehr`, `patient-context`, specyfikacje komponentów | gotowe |
| F3 Recepty | `drug`, `prescription` (+ drug-safety-checks), stuby `testing/drug-service.stub.ts`, `testing/prescription-service.stub.ts` | gotowe |
| F4 Laboratorium | `lab-order`, `lab-result` (stuby `testing/lab-order-service.stub.ts`, `testing/lab-result-service.stub.ts`), mapowanie 422 `errors[]` na pola kreatora zlecenia | gotowe |
| F5 Obrazowanie | `imaging-order`, `imaging-result` (stuby `testing/imaging-order-service.stub.ts`, `testing/imaging-result-service.stub.ts`), worklista zleceń z osobną mapą przejść obrazowych i uprawnieniami, anulowanie ze `version`, kreator (422 na pola, 409 zajęty slot), `ResultWithPatient.patient` opcjonalny | gotowe |
| F6 Pozostałe | `vitals` (progi z `GET /vital-thresholds`, anomalie i `ward-overview` z backendu, 422 na pola formularza), `team-message` (stan `unreadCount`/`alerts` odświeżany `refresh()` przy starcie powłoki; zadania ze `version` i tylko dla przypisanego/twórcy; `GET /message-threads/{id}` dla głębokiego linku; `ClinicalAlert.target` -> trasa w `utils/alert-route.ts`), `dashboard` (`GET /dashboard/stats`; wiersze inboxu bez `patient` rozwiązywane po `patientId`); stuby `testing/vitals-service.stub.ts`, `testing/team-message-service.stub.ts`, `testing/dashboard-service.stub.ts` | gotowe |
| F7 STOMP | `services/realtime.service.ts` na `@stomp/stompjs`: `/ws` (JWT w CONNECT), połączenie wg sesji, reconnect z backoffem + `refresh()`, push -> `TeamMessageService.applyPush`/`pushed$` (strony wiadomości), status w nagłówku; stub `testing/realtime-service.stub.ts` | gotowe |
| F8 Domknięcie | rejestracja z publicznym `GET /auth/register/wards` (`PublicWard`); opcjonalne `Admission.wardId`/`attendingPhysicianId`/`reason` (przyjęcie `outpatient` z samym `{admissionType, admittedAt}`, formularz ambulatoryjny wykonuje przyjęcie); `PatientHistoryPage` bez `ehr:read` pokazuje tylko sekcje `ehr:read-limited`, sekcje ładują się niezależnie; uprawnienia zleceń lab w UI (`lab-order:update-status`, `lab-order:collect-specimen`, `lab-order:cancel`); usunięte `mockResponse` i `MOCK_LATENCY_MS`; `@stomp/stompjs` w `allowedCommonJsDependencies`; generator czyta progi z `mock-data/vital-thresholds.mock.ts` | gotowe |

Uwaga: po zalogowaniu na konto demo (`stf-*` w mockach vs UUID w bazie) część widoków może być pusta; do testów używać kont `EMP-0001..EMP-0010`.

### Pozostało (poza integracją frontendu)

1. **Ograniczenia symulatorów `e-receipt`, `e-laboratory`, `e-imaging`:** bez ponawiania wysyłki z his_backend (nieudana wysyłka nie jest powtarzana; w e-receipt zostaje klucz lokalny, zlecenie lab/obrazowe nie trafia do usługi), stan w pamięci, UI niedostępne na produkcji (brak publikacji portów `e-*`); zmiany stanu wykonane w HIS (poza anulowaniem) nie są przekazywane do e-laboratory ani e-imaging.
2. **Dobór terminologii wg specjalizacji lekarza** (README): jest `GET /terminology/snomed/suggestions?kind=diagnosis|symptom|procedure` (ECL per specjalizacja zalogowanego lekarza z `his.terminology.suggestions.*`, zestaw domyślny dla nieznanej/pustej); frontend jeszcze go nie używa, składnia ECL profili nie była sprawdzona na lokalnym Lite. Otwarte: relacja do słownika `icd10_code` / `/dictionaries/icd-10` (README zakłada SCTID).
3. **Smoke test frontu na prawdziwym backendzie** (konta `EMP-*`): nie wykonany; generator `pnpm export:mocks` nie uruchamiany po zmianach F8 (zapisuje do `his_backend`), sprawdzony tylko typecheckiem.
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
| Wygasanie recept | wyliczane przy odczycie; brak schedulera i zdarzeń; `expired` ustawiany też przez e-receipt (`PUT /fhir/MedicationRequest/{id}`) |
| STOMP | token JWT sprawdzany tylko przy CONNECT; rejestr "online" per instancja backendu |
| Uprawnienia bez egzekwowania | `staff:write`, `ward:write`, `vital-threshold:write`, `lab-result:write`, `imaging-result:write` są w tokenie, ale żaden kontroler ich nie sprawdza |
| Konta demo | `db/changelog/demo/` ma kontekst `reference`, więc konta ładują się zawsze, także na produkcji (istnieją tylko konteksty `reference` i `mock`) |
| Zdarzenia bez konsumenta | `PatientAdmitted`, `PatientDischarged`, trzy zdarzenia EHR (zdarzenia recept ma `EReceiptIntegration`) |
| Niezweryfikowane uruchomieniem | czy `anyRequest().denyAll()` daje 401 czy 403 poza `/api/**`, `/ws/**`, `/actuator/health/**` |

## Operacje na środowisku

- **Serwer produkcyjny (192.168.1.160):** w `config.env` ustawić `HIS_DB_NAME`, `HIS_DB_USER`, `HIS_DB_PASSWORD`, `HIS_JWT_SECRET` (min. 32 bajty, wymagane); opcjonalnie `HIS_JWT_TTL`, `HIS_JWT_ISSUER`, `HIS_LOCKOUT_*`, `HIS_CORS_ALLOWED_ORIGINS`, `HIS_WS_ALLOWED_ORIGINS`, `HIS_LIQUIBASE_CONTEXTS`, `COMPOSE_PROFILES=terminology`, `HIS_SNOWSTORM_*`, hasła mTLS (osiem `*_KEYSTORE_PASSWORD` / `*_TRUSTSTORE_PASSWORD` z `.certs/passwords.env`) i pliki `.p12` w `/home/docker_deploy/.certs` (zob. `production_deployment.md`; certyfikaty ważne do 2027-10-01). Zmienić hasła kont demo po pierwszym wdrożeniu.
- Wolumeny stosu: `hospital-information-system_postgres-data`, `hospital-information-system_snowstorm-lite-data`; indeks Snowstorm wymaga importu RF2.
- Jeśli porty 5432/8080 są zajęte, ustawić `HIS_DB_HOST_PORT` / `HIS_SNOWSTORM_HOST_PORT` przed `pnpm stack:up`.

## Komendy

| Scenariusz | Komenda |
| --- | --- |
| Pełny stos lokalnie | `docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env up -d --build` (`pnpm stack:up`; ze Snowstormem `--profile terminology`) |
| Tryb IDE | `pnpm stack:db` (sam postgres), potem `pnpm start:backend` (profil `dev`: kontekst `reference,mock`; bez mTLS: `HIS_MTLS_ENABLED=false`, więc bez `/fhir/**`) i `pnpm start` (proxy -> 10420). Nie uruchamiać kontenera `his-backend` (konflikt portu 10420) |
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
