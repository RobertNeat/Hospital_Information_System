# Plan prac: HIS (stan na 2026-10-02)

Dokument zawiera wyłącznie prace niedokończone oraz stałe informacje operacyjne. Zakończone elementy są usuwane (stan faktyczny: kod i `README.md`).

## Architektura i założenia

`his_frontend` --(REST + STOMP)--> `his_backend` --(FHIR, mTLS)--> `e-receipt`, `e-laboratory`, `e-imaging`; `his_backend` --> Snowstorm Lite 2.7.0 (SNOMED CT, domyślnie wyłączony) i PostgreSQL. Szczegóły: `README.md` (root), wdrożenie: `docs/deployment-and-config.md`, baza: `docs/database-and-data.md`.

- SQL przechowuje wyłącznie SCTID; treść serwuje Snowstorm Lite (ECL). Dane licencjonowane nie są w danych początkowych.
- Konta demo (login = hasło) także na produkcji; ryzyko słabych haseł w LAN zaakceptowane.
- Biblioteki: HAPI FHIR 8.12.1 (klient HTTP: Spring `RestClient`), Testcontainers `postgres:17-alpine`, vitest dla generatora mocków (`pnpm export:mocks`).

## Kontrakty (różnicowe)

Katalogi `docs/*_contract` przechowują wyłącznie kontrakty **jeszcze niezaimplementowane** (po wdrożeniu pozycja jest usuwana, bez opisu zaimplementowanych kontraktów):

- `docs/his_backend_contract/README.md` - braki oferty backendu względem oczekiwań frontendu (endpointy zapisu, uprawnienia nieegzekwowane, sesja/STOMP, integracje FHIR, zdarzenia bez konsumenta, terminologia, paginacja).
- `docs/his_frontend_contract/README.md` - luki UI względem backendu, oczekiwania bez pokrycia, porządki w modelach.

Po zaimplementowaniu pozycji z kontraktu usunąć ją z odpowiedniego README w tej samej zmianie. Nowy kontrakt między modułami dopisywać tam, dopóki nie jest zrealizowany.

## Do dokończenia

1. **Ograniczenia symulatorów `e-receipt`, `e-laboratory`, `e-imaging`:** bez ponawiania wysyłki z his_backend (nieudana wysyłka nie jest powtarzana; w e-receipt zostaje klucz lokalny, zlecenie lab/obrazowe nie trafia do usługi), stan w pamięci, UI niedostępne na produkcji (brak publikacji portów `e-*`); zmiany stanu wykonane w HIS (poza anulowaniem) nie są przekazywane do e-laboratory ani e-imaging.
2. **Dobór terminologii wg specjalizacji lekarza:** backend ma `GET /terminology/snomed/suggestions?kind=diagnosis|symptom|procedure`; frontend go nie używa, składnia ECL profili (`his.terminology.suggestions.*`) nie była sprawdzona na lokalnym Lite. Otwarte: relacja do słownika `icd10_code` / `/dictionaries/icd-10` (README zakłada SCTID).
3. **Smoke test frontu:** do sprawdzenia pozostają elementy z listy "Niezweryfikowane w teście"; generator `pnpm export:mocks` nie był uruchamiany po zmianach F8 (zapisuje do `his_backend`), sprawdzony tylko typecheckiem. Build frontu ostrzega o budżecie initial 500 kB (849 kB; limit bez zmian).
4. **Weryfikacja bezpieczeństwa w CI:** `trivy fs`, `semgrep` i `validate_projects.sh` (wymaga `jq`) nie były uruchamiane lokalnie; sprawdzić w pierwszym przebiegu CI (hasła demo, placeholdery `change-me`, hashe BCrypt w SQL mogą zostać oflagowane).
5. **Luki kontraktowe:** pozycje z obu README kontraktów (m.in. zapis diagnoz/alergii i aktywacja kont w UI, endpointy zapisu `staff`/`wards`/`vital-thresholds`, bramkowanie uprawnieniami w UI).

Uwaga: po zalogowaniu na konto demo (`stf-*` w mockach vs UUID w bazie) część widoków może być pusta; do testów używać kont `EMP-0001..EMP-0010`.

### Problemy wykryte w teście lokalnym

| # | Priorytet | Problem | Miejsce / poprawka |
| --- | --- | --- | --- |
| P1 | wysoki | Modal "e-Recepta wystawiona" pokazuje klucz lokalny z odpowiedzi 201; właściwy `eRxKey` z e-receipt zapisuje się w HIS dopiero po commicie | frontend: ponowne pobranie `GET /prescriptions/{id}` przed pokazaniem klucza albo zwracanie klucza po integracji (backend) |
| P2 | średni | Kreator recepty pisze, że integracja FHIR jest "planowana, tryb demonstracyjny" | frontend: `components/fhir-integration-note` |
| P3 | średni | Frontend nie bramkuje tras i wywołań uprawnieniami (trasy mają tylko `authGuard`): role bez uprawnień dostają 403 i nieobsłużone `ApiError` (laborant: vitals, imaging, recepty; farmaceuta: wyniki, zlecenia, vitals, wiadomości); widoczne przyciski bez uprawnień ("Nowa recepta", "Zlecenie badania obrazowego"); zakładka "Leki i recepty" pokazuje błędny komunikat "Brak aktywnych leków" | frontend: warunkować wywołania i menu uprawnieniami z tokenu, obsłużyć `ApiError` w komponentach |
| P4 | niski | Lista "Recepty" pokazuje UUID zamiast nazwiska pacjenta (`prescriptions-list-page.ts`); `his.currentPatientId` (sessionStorage) nie jest czyszczone przy wylogowaniu | frontend: kolumna pacjenta; `PatientContextService.clear()` przy wylogowaniu |
| P5 | niski/średni | Nieznany SCTID daje 503 zamiast 404 (Snowstorm Lite 2.7.0 odpowiada 500 na `$lookup` nieistniejącego kodu) | backend: `SnowstormClient.lookup` traktować 500 z `$lookup` jako 404 lub sprawdzać istnienie przez `$expand` |
| P6 | średni | ECL `procedure` profilu chorób wewnętrznych/rodzinnej (`<< 103693007 OR << 387713003`) zwraca prawie wyłącznie zabiegi chirurgiczne (`103693007` nie ma potomków w edycji 20261001) | backend: `application.properties` (`his.terminology.suggestions.*`), proponowane `<< 386053000 OR << 387713003`; bez parametru `term` kolejność wyników jest alfabetyczna/przypadkowa |
| P7 | niski | `GlobalExceptionHandler` loguje `ERROR` przy zerwaniu połączenia przez klienta (broken pipe, `HttpMessageNotWritableException`) | backend: obsłużyć `ClientAbortException` / `AsyncRequestNotUsableException` na poziomie DEBUG |
| P8 | kosmetyczny | Komunikat alertu krytycznego laboratorium dubluje kod: "TROP (TROP)" | backend: treść alertu `critical_result` |

Niezweryfikowane w teście: wysłanie formularza rejestracji konta, kreator zleceń lab/obrazowych i przejście `outpatient` w UI, widoki UI dla admina, radiologa, rejestratora i pielęgniarki, wywołanie FHIR certyfikatem klienta z hosta, STOMP dla alertu krytycznego; zmiany stanu w e-laboratory i e-imaging sprawdzono formularzami POST (curl), nie klikaniem w przeglądarce. Zachowanie (nie błąd): automatyczna flaga wyniku GLU=450 to `H`; krytyczność (`HH`) wymaga jawnej flagi z e-laboratory.

## Operacje na środowisku

- **Serwer produkcyjny (192.168.1.160):** w `config.env` ustawić `HIS_DB_NAME`, `HIS_DB_USER`, `HIS_DB_PASSWORD`, `HIS_JWT_SECRET` (min. 32 bajty, wymagane); opcjonalnie `HIS_JWT_TTL`, `HIS_JWT_ISSUER`, `HIS_LOCKOUT_*`, `HIS_CORS_ALLOWED_ORIGINS`, `HIS_WS_ALLOWED_ORIGINS`, `HIS_LIQUIBASE_CONTEXTS`, `COMPOSE_PROFILES=terminology`, `HIS_SNOWSTORM_*`, hasła mTLS (osiem `*_KEYSTORE_PASSWORD` / `*_TRUSTSTORE_PASSWORD` z `.certs/passwords.env`) i pliki `.p12` w `/home/docker_deploy/.certs` (zob. `.github/pipeline_docs/production_deployment.md`; certyfikaty ważne do 2027-10-01). Zmienić hasła kont demo po pierwszym wdrożeniu.
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

## Odnośniki

- `docs/his_frontend_contract/`, `docs/his_backend_contract/` - niezaimplementowane kontrakty (różnicowo).
- `docs/deployment-and-config.md`, `docs/database-and-data.md` - wdrożenie, konfiguracja, baza.
- `apps/his_frontend/scripts/export-mocks/README.md` - generator danych mock i polityka zmian changesetów.
- `.github/pipeline_docs/production_deployment.md` - deploy, zmienne środowiskowe, Postgres, Snowstorm, konta demo.
- `README.md` (root) - uruchamianie lokalne i import RF2 do Snowstorm Lite.
- Zasady pracy: `CLAUDE.md`.
