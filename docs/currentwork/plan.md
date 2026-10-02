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

1. **Ograniczenia symulatorów `e-receipt`, `e-laboratory`, `e-imaging`:** bez ponawiania wysyłki z his_backend (nieudana wysyłka nie jest powtarzana; w e-receipt zostaje klucz lokalny, zlecenie lab/obrazowe nie trafia do usługi), stan w pamięci, UI niedostępne na produkcji (brak publikacji portów `e-*`).

Uwaga: po zalogowaniu na konto demo (`stf-*` w mockach vs UUID w bazie) część widoków może być pusta; do testów używać kont `EMP-0001..EMP-0010`.

**Smoke test frontu (2026-10-02, tryb IDE, konta doctor/admin/nurse) - zweryfikowane działające:** logowanie i pulpit (doctor, admin, nurse - nawigacja poprawnie zależna od roli), lista i karta pacjenta, zapis rozpoznania SNOMED (dialog + picker z podpowiedziami, zapis przez `POST /patients/{id}/diagnoses`, natychmiastowy refresh listy), zapis alergii (`POST /patients/{id}/allergies`), kreator zlecenia laboratoryjnego pełny przepływ (wybór badań -> materiał/pilność -> rozpoznanie SNOMED + informacje kliniczne -> podsumowanie -> wysłanie, status "Zlecone" widoczny od razu), widok administratora `/admin/staff` (lista, aktywacja/blokada konta - przetestowane programowo przez wywołanie metod komponentu, bo klikanie w PrimeNG `ConfirmDialog` nie rejestrowało się w tej sesji automatyzacji przeglądarki mimo poprawnego działania aplikacji), STOMP dla alertów (status `connected`, wymaga `HIS_WS_ALLOWED_ORIGINS=http://localhost:10400` przy ręcznym uruchomieniu `spring-boot:run` - patrz poprawka w sekcji "Komendy" niżej, bez tego połączenie WS odrzucane na `setAllowedOrigins`, UI zostaje w stanie "łączenie..."). Naprawiono przy okazji: `lab-order-wizard-page.ts` krok podsumowania czytał `FormControl.value` bezpośrednio w `computed()` (sygnał nigdy się nie przeliczał po zmianie pól `Rozpoznanie`/`Informacje kliniczne`/`Pilność`/`Na czczo`/`Planowany termin pobrania` - podsumowanie pokazywało stare/puste wartości mimo poprawnych danych w formularzu i w rzeczywistym zapisie); naprawiono przez `rawValueSignal` (wzorzec już używany w `imaging-order-wizard-page.ts`). Usunięto też zdublowany, nieużywany `<p-confirmdialog key="account-action">` w `admin-staff-page.html` (jedyny realny `<p-confirmdialog>` to globalny w `app.html`, bez klucza).

Niezweryfikowane w teście: wysłanie formularza rejestracji konta, przejście `outpatient` w UI, widoki radiologa i rejestratora (zablokowane przez nieoczekiwaną awarię wpisywania tekstu w polach logowania pod koniec sesji testowej - do powtórzenia), wywołanie FHIR certyfikatem klienta z hosta; zmiany stanu w e-laboratory i e-imaging sprawdzono formularzami POST (curl), nie klikaniem w przeglądarce. Zachowanie (nie błąd): automatyczna flaga wyniku GLU=450 to `H`; krytyczność (`HH`) wymaga jawnej flagi z e-laboratory.

## Operacje na środowisku

- **Serwer produkcyjny (192.168.1.160):** w `config.env` ustawić `HIS_DB_NAME`, `HIS_DB_USER`, `HIS_DB_PASSWORD`, `HIS_JWT_SECRET` (min. 32 bajty, wymagane); opcjonalnie `HIS_JWT_TTL`, `HIS_JWT_ISSUER`, `HIS_LOCKOUT_*`, `HIS_CORS_ALLOWED_ORIGINS`, `HIS_WS_ALLOWED_ORIGINS`, `HIS_LIQUIBASE_CONTEXTS`, `COMPOSE_PROFILES=terminology`, `HIS_SNOWSTORM_*`, hasła mTLS (osiem `*_KEYSTORE_PASSWORD` / `*_TRUSTSTORE_PASSWORD` z `.certs/passwords.env`) i pliki `.p12` w `/home/docker_deploy/.certs` (zob. `.github/pipeline_docs/production_deployment.md`; certyfikaty ważne do 2027-10-01). Zmienić hasła kont demo po pierwszym wdrożeniu.
- Wolumeny stosu: `hospital-information-system_postgres-data`, `hospital-information-system_snowstorm-lite-data`; indeks Snowstorm wymaga importu RF2.
- Jeśli porty 5432/8080 są zajęte, ustawić `HIS_DB_HOST_PORT` / `HIS_SNOWSTORM_HOST_PORT` przed `pnpm stack:up`.

## Komendy

| Scenariusz          | Komenda                                                                                                                                                                                                                                                      |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Pełny stos lokalnie | `docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env up -d --build` (`pnpm stack:up`; ze Snowstormem `--profile terminology`)                                                                                         |
| Tryb IDE            | `pnpm stack:db` (sam postgres), potem `pnpm start:backend` (profil `dev`: kontekst `reference,mock`; bez mTLS: `HIS_MTLS_ENABLED=false`, więc bez `/fhir/**`; dla STOMP ustawić `HIS_WS_ALLOWED_ORIGINS=http://localhost:10400`, inaczej uchwyt WS jest odrzucany i UI zostaje w stanie "łączenie...") i `pnpm start` (proxy -> 10420). Nie uruchamiać kontenera `his-backend` (konflikt portu 10420) |
| Weryfikacja         | `pnpm lint`, `pnpm typecheck`, `pnpm format:check`, `pnpm test:frontend`, `cd apps/his_backend; .\mvnw.cmd verify` (wymaga Dockera dla Testcontainers); to samo `verify` w `apps/e-receipt`, `apps/e-laboratory`, `apps/e-imaging`                           |
| Regeneracja mocków  | `pnpm export:mocks` (wynik: `apps/his_backend/src/main/resources/db/changelog/{reference,mock}`; wykonanych changesetów schematu nie edytować)                                                                                                               |

Logowanie: konta demo `admin/admin`, `user/user` (lekarz), `doctor`, `nurse`, `lab-tech`, `radiologist`, `pharmacist`, `registrar` (hasło = login); konta mocka `EMP-0001..EMP-0010` / `HisDemo2026!`.

## Odnośniki

- `docs/his_frontend_contract/`, `docs/his_backend_contract/` - niezaimplementowane kontrakty (różnicowo).
- `docs/deployment-and-config.md`, `docs/database-and-data.md` - wdrożenie, konfiguracja, baza.
- `apps/his_frontend/scripts/export-mocks/README.md` - generator danych mock i polityka zmian changesetów.
- `.github/pipeline_docs/production_deployment.md` - deploy, zmienne środowiskowe, Postgres, Snowstorm, konta demo.
- `README.md` (root) - uruchamianie lokalne i import RF2 do Snowstorm Lite.
- Zasady pracy: `CLAUDE.md`.
