# Wdrożenie i konfiguracja

Usługi compose, porty, sieci, zmienne środowiskowe `HIS_*` i uruchamianie lokalne. Powrót: [README.md](README.md).

Źródła: `deploy/compose.yml`, `deploy/compose.dev.yml`, `deploy/local.env(.example)`, `.env.example`, `.github/ci/projects.json`, `apps/his_backend/src/main/resources/application*.properties`, `apps/his_frontend/nginx.conf`, `apps/his_frontend/proxy.conf.json`. Procedury produkcyjne: `.github/pipeline_docs/production_deployment.md`.

## Usługi i porty

Porty z `.github/ci/projects.json` (host = kontener):

| Usługa (compose) | Obraz | Port | Publikacja na hoście | Sieci | Uwagi |
| --- | --- | --- | --- | --- | --- |
| `postgres` | `postgres:17-alpine` | 5432 | tylko overlay dev: `127.0.0.1:${HIS_DB_HOST_PORT:-5432}` | `his-internal` (+ `default` w dev) | wolumen `postgres-data`; healthcheck `pg_isready` |
| `his-backend` | `${REGISTRY}/${HIS_BACKEND_IMAGE}:${IMAGE_TAG}` | **10420** | prod: `0.0.0.0:${HIS_BACKEND_HOST_PORT:-10420}`; dev: `127.0.0.1:...` | `default` + `his-internal` | `depends_on: postgres (healthy)`; healthcheck `curl /actuator/health/readiness` (start_period 60 s) |
| `his-frontend` | `.../his-frontend` | **10400** | prod: `0.0.0.0:${HIS_FRONTEND_HOST_PORT:-10400}`; dev: `127.0.0.1:...` | `default` | nginx; proxy `/api/` i `/ws` do `http://his-backend:10420` (Docker DNS `127.0.0.11`, host rozwiązywany per żądanie) |
| `e-receipt` | `.../e-receipt` | **10421** | tylko overlay dev (loopback) | `his-internal` (+ `default` w dev) | health, UI Thymeleaf `/ui/prescriptions`, FHIR `/fhir/MedicationRequest` |
| `e-laboratory` | `.../e-laboratory` | **10422** | tylko overlay dev (loopback) | j.w. | health, UI Thymeleaf `/ui/orders`, FHIR `/fhir/ServiceRequest` |
| `e-imaging` | `.../e-imaging` | **10423** | tylko overlay dev (loopback) | j.w. | health, UI Thymeleaf `/ui/orders`, FHIR `/fhir/ServiceRequest` |
| `snowstorm-lite-init` | `snomedinternational/snowstorm-lite:2.7.0` | - | - | `network_mode: none` | profil `terminology`; jednorazowo ustawia właściciela wolumenu (`chown 1000:1000`) |
| `snowstorm-lite` | `snomedinternational/snowstorm-lite:2.7.0` | 8080 | tylko overlay dev: `127.0.0.1:${HIS_SNOWSTORM_HOST_PORT:-8080}` | `his-internal` (+ `default` w dev) | profil **`terminology`**; wolumen `snowstorm-lite-data`; healthcheck `GET /fhir/metadata`; hasło admina `HIS_SNOWSTORM_ADMIN_PASSWORD` |

Sieci: `his-internal` (`internal: true`, bez dostępu na zewnątrz) łączy backend z bazą, usługami `e-*` i Snowstorm; `default` służy do publikacji portów. Backend **nie ma `depends_on`** od `snowstorm-lite` (terminologia opcjonalna).

Obrazy budowane z `.github/docker/spring.Dockerfile` (backend, `e-*`) i `.github/docker/angular.Dockerfile` (frontend); wersje runtime: Java 25, Node 22.

## Usługi `e-*`

`e-receipt` (10421) jest symulatorem e-recept: FHIR R4 `POST/GET/PUT /fhir/MedicationRequest[/{eRxKey}]` (HAPI), UI Thymeleaf pod `/` -> `/ui/prescriptions` (lista recept, zmiana stanu `partially_dispensed`/`dispensed`/`cancelled`/`expired`, ponowienie wysyłki do HIS) oraz `/actuator/health/**`. Wszystko to jest publiczne (bez uwierzytelniania aplikacyjnego), reszta `denyAll` (`SecurityConfig`). Stan recept trzymany jest **w pamięci** (brak bazy; restart czyści listę, a HIS pozostaje autorytatywny). Zmiana stanu z UI jest najpierw wysyłana do HIS (`PUT {his}/fhir/MedicationRequest/{id}` z `X-Service-Key`): odrzucenie przez HIS (4xx) blokuje zmianę, niedostępność HIS zostawia zmianę lokalną ze stanem synchronizacji `PENDING` (przycisk ponowienia). Szczegóły: [rest-api-fhir.md](rest-api-fhir.md).

`e-laboratory` (10422) jest symulatorem laboratorium: FHIR R4 `POST/GET/PUT /fhir/ServiceRequest[/{id zlecenia HIS}]` (HAPI), UI Thymeleaf pod `/` -> `/ui/orders` (lista zleceń, zmiana stanu `scheduled`/`specimen_collected`/`in_progress`/`completed`/`cancelled`, formularz wyniku z obserwacjami dla badań zlecenia, ponowienie wysyłki stanu i wyniku do HIS) oraz `/actuator/health/**`; publiczne bez uwierzytelniania aplikacyjnego, reszta `denyAll` (`SecurityConfig`). Stan zleceń i wyników trzymany jest **w pamięci** (restart czyści listę, HIS jest autorytatywny). Zmiana stanu z UI jest najpierw wysyłana do HIS (`PUT {his}/fhir/ServiceRequest/{id}`), wynik jako `POST {his}/fhir/DiagnosticReport` (oba z `X-Service-Key`): odrzucenie przez HIS (4xx) blokuje zmianę, niedostępność HIS zostawia ją lokalnie ze stanem synchronizacji `PENDING` (przyciski ponowienia). Gdy każda pozycja ma zatwierdzony i przekazany wynik, zlecenie przechodzi lokalnie do `completed` (jak auto-`completed` w HIS). Szczegóły: [rest-api-fhir.md](rest-api-fhir.md#badania-laboratoryjne-e-laboratory).

`e-imaging` (10423) jest symulatorem pracowni obrazowej: FHIR R4 `POST/GET/PUT /fhir/ServiceRequest[/{id zlecenia HIS}]` (HAPI), UI Thymeleaf pod `/` -> `/ui/orders` (lista zleceń z badaniem, modalnością, lateralnością, kontrastem, wskazaniem, pilnością i terminem; zmiana stanu `ordered`/`scheduled`/`in_progress`/`completed`/`cancelled`; formularz wyniku: opis, wnioski, flaga krytyczny, radiolog; ponowienie wysyłki stanu i wyniku do HIS) oraz `/actuator/health/**`; publiczne bez uwierzytelniania aplikacyjnego, reszta `denyAll` (`SecurityConfig`). Stan zleceń i wyników trzymany jest **w pamięci** (restart czyści listę, HIS jest autorytatywny); stan początkowy zlecenia pochodzi z HIS (zlecenie ze slotem przychodzi jako `scheduled`). Zmiana stanu z UI jest najpierw wysyłana do HIS (`PUT {his}/fhir/ServiceRequest/{id}`), wynik jako `POST {his}/fhir/DiagnosticReport` (oba z `X-Service-Key`): odrzucenie przez HIS (4xx) blokuje zmianę, niedostępność HIS zostawia ją lokalnie ze stanem synchronizacji `PENDING` (przyciski ponowienia). Formularz wyniku jest dostępny w stanach `scheduled` i `in_progress`; wynik `final` przekazany do HIS przenosi zlecenie lokalnie do `completed` (jak auto-`completed` w HIS). Szczegóły: [rest-api-fhir.md](rest-api-fhir.md#badania-obrazowe-e-imaging).

`his-backend` wywołuje `e-receipt` tylko gdy `HIS_ERECEIPT_ENABLED=true`, `e-laboratory` tylko gdy `HIS_ELAB_ENABLED=true`, a `e-imaging` tylko gdy `HIS_EIMG_ENABLED=true` (wszystkie domyślnie `false`); `his-backend` nie ma `depends_on` od `e-*`.

### Profil `mtls` (FHIR)

Aktywacja `--spring.profiles.active=mtls`. W `e-*`: serwer TLS z `client-auth=need` (paczka `server`). W `his-backend`: paczka SSL `fhir-client` (keystore/truststore PKCS12). **mTLS nie jest dziś włączony ani wymagany**: wywołania FHIR korzystają z klucza usługowego (`X-Service-Key`), a profil `mtls` nie jest używany w compose (`client-auth=need` uniemożliwia zwykły healthcheck bez osobnego portu zarządzania). Klienty FHIR są przygotowane do mTLS: `his.integration.ereceipt.ssl-bundle` i `his.integration.elab.ssl-bundle` i `his.integration.eimg.ssl-bundle` (HIS) oraz `ereceipt.his.ssl-bundle` i `elaboratory.his.ssl-bundle` i `eimaging.his.ssl-bundle` (e-*) wskazują nazwę paczki `spring.ssl.bundle.*` (np. `fhir-client`, `server`) i są przekazywane do `HttpClientSettings.withSslBundle`; po wdrożeniu certyfikatów wystarczy ustawić właściwość i URL `https://...`, a klucz usługowy usunąć. Zmienne: `MTLS_KEY_ALIAS`, `MTLS_KEYSTORE`, `MTLS_KEYSTORE_PASSWORD`, `MTLS_TRUSTSTORE`, `MTLS_TRUSTSTORE_PASSWORD`.

## Zmienne środowiskowe backendu

Kolumny: **Aplikacja** = wartość domyślna w `application.properties`; **dev** = nadpisanie w profilu `dev`; **Compose** = co przekazuje `deploy/compose.yml` do kontenera `his-backend` (`environment:`; zmienne z `config.env` trafiają dodatkowo przez `env_file`, `required: false`); **Wymagana** = backend nie wystartuje bez wartości.

| Zmienna | Znaczenie | Aplikacja | dev | Compose | Wymagana |
| --- | --- | --- | --- | --- | --- |
| `HIS_DB_URL` | JDBC URL | brak | `jdbc:postgresql://localhost:5432/his` | `jdbc:postgresql://postgres:5432/${HIS_DB_NAME:-his}` (na sztywno) | tak |
| `HIS_DB_USER` | użytkownik DB | brak | `his` | `${HIS_DB_USER:-his}` | tak |
| `HIS_DB_PASSWORD` | hasło DB | brak | `his` | `${HIS_DB_PASSWORD:-his}` | tak |
| `HIS_LIQUIBASE_CONTEXTS` | konteksty Liquibase | `reference` | `reference,mock` | `${HIS_LIQUIBASE_CONTEXTS:-reference}` | nie |
| `HIS_JWT_SECRET` | klucz HS256, min. 32 bajty | **pusty = błąd startu** | jawny klucz deweloperski | `${HIS_JWT_SECRET:-}` (pusty = backend nie wystartuje) | **tak** |
| `HIS_JWT_TTL` | czas życia tokenu | `8h` | - | `${HIS_JWT_TTL:-8h}` | nie |
| `HIS_JWT_ISSUER` | issuer JWT | `his-backend` | - | `${HIS_JWT_ISSUER:-his-backend}` | nie |
| `HIS_LOCKOUT_MAX_ATTEMPTS` | próby do blokady | `5` | - | `${HIS_LOCKOUT_MAX_ATTEMPTS:-5}` | nie |
| `HIS_LOCKOUT_DURATION` | czas blokady | `15m` | - | `${HIS_LOCKOUT_DURATION:-15m}` | nie |
| `HIS_CORS_ALLOWED_ORIGINS` | origin CORS (po przecinku); puste = CORS wyłączony | puste | - | `${HIS_CORS_ALLOWED_ORIGINS:-}` | nie |
| `HIS_WS_ALLOWED_ORIGINS` | origin handshake WebSocket; puste = same-origin | puste | - | `${HIS_WS_ALLOWED_ORIGINS:-}` | nie |
| `HIS_SNOWSTORM_ENABLED` | włącza integrację SNOMED | `false` | - | `${HIS_SNOWSTORM_ENABLED:-false}` | nie |
| `HIS_SNOWSTORM_URL` | baza FHIR Snowstorm | `http://localhost:8080/fhir` | - | `http://snowstorm-lite:8080/fhir` (na sztywno) | nie |
| `HIS_SNOWSTORM_CONNECT_TIMEOUT` | timeout połączenia | `2s` | - | `${HIS_SNOWSTORM_CONNECT_TIMEOUT:-2s}` | nie |
| `HIS_SNOWSTORM_READ_TIMEOUT` | timeout odczytu | `5s` | - | `${HIS_SNOWSTORM_READ_TIMEOUT:-5s}` | nie |
| `HIS_SNOWSTORM_DISPLAY_LANGUAGE` | `displayLanguage` | `pl,en` | - | `${HIS_SNOWSTORM_DISPLAY_LANGUAGE:-pl,en}` | nie |
| `HIS_SNOWSTORM_MAX_PAGE_SIZE` | maks. `limit` | `100` | - | `${HIS_SNOWSTORM_MAX_PAGE_SIZE:-100}` | nie |
| `HIS_FHIR_SERVICE_KEY` | klucz usługowy (`X-Service-Key`) autoryzujący `/fhir/**`; pusty = wszystkie żądania 401 | pusty | - | `${HIS_FHIR_SERVICE_KEY:-}` | nie (bez niego e-receipt, e-laboratory i e-imaging nie zmienią stanu recept / zleceń) |
| `HIS_ERECEIPT_ENABLED` | włącza klienta e-receipt (wysyłka recept `e_prescription`, anulowanie) | `false` | - | `${HIS_ERECEIPT_ENABLED:-false}` | nie |
| `HIS_ERECEIPT_URL` | baza FHIR e-receipt | `http://localhost:10421/fhir` | - | `http://e-receipt:10421/fhir` (na sztywno) | nie |
| `HIS_ERECEIPT_CONNECT_TIMEOUT` | timeout połączenia | `1s` | - | `${HIS_ERECEIPT_CONNECT_TIMEOUT:-1s}` | nie |
| `HIS_ERECEIPT_READ_TIMEOUT` | timeout odczytu | `3s` | - | `${HIS_ERECEIPT_READ_TIMEOUT:-3s}` | nie |
| `HIS_ERECEIPT_SSL_BUNDLE` | nazwa paczki `spring.ssl.bundle.*` dla mTLS (puste = bez SSL bundle) | puste | - | nie | nie |
| `HIS_ELAB_ENABLED` | włącza klienta e-laboratory (wysyłka zleceń lab, anulowanie) | `false` | - | `${HIS_ELAB_ENABLED:-false}` | nie |
| `HIS_ELAB_URL` | baza FHIR e-laboratory | `http://localhost:10422/fhir` | - | `http://e-laboratory:10422/fhir` (na sztywno) | nie |
| `HIS_ELAB_CONNECT_TIMEOUT` | timeout połączenia | `1s` | - | `${HIS_ELAB_CONNECT_TIMEOUT:-1s}` | nie |
| `HIS_ELAB_READ_TIMEOUT` | timeout odczytu | `3s` | - | `${HIS_ELAB_READ_TIMEOUT:-3s}` | nie |
| `HIS_ELAB_SSL_BUNDLE` | nazwa paczki `spring.ssl.bundle.*` dla mTLS (puste = bez SSL bundle) | puste | - | nie | nie |
| `HIS_EIMG_ENABLED` | włącza klienta e-imaging (wysyłka zleceń obrazowych, anulowanie) | `false` | - | `${HIS_EIMG_ENABLED:-false}` | nie |
| `HIS_EIMG_URL` | baza FHIR e-imaging | `http://localhost:10423/fhir` | - | `http://e-imaging:10423/fhir` (na sztywno) | nie |
| `HIS_EIMG_CONNECT_TIMEOUT` | timeout połączenia | `1s` | - | `${HIS_EIMG_CONNECT_TIMEOUT:-1s}` | nie |
| `HIS_EIMG_READ_TIMEOUT` | timeout odczytu | `3s` | - | `${HIS_EIMG_READ_TIMEOUT:-3s}` | nie |
| `HIS_EIMG_SSL_BUNDLE` | nazwa paczki `spring.ssl.bundle.*` dla mTLS (puste = bez SSL bundle) | puste | - | nie | nie |
| `MTLS_*` | paczki SSL profilu `mtls` | - | - | nie | tylko z profilem `mtls` |

Stałe ustawienia (bez zmiennej): `server.port=10420`, paginacja domyślnie 20 / maks. 100, `management.endpoints.web.exposure.include=health` (probes włączone, brak szczegółów), `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`.

Zmienne `e-receipt` (`ereceipt.his.*`; compose przekazuje je do kontenera `e-receipt`): `ERECEIPT_HIS_ENABLED` (domyślnie `false`; wywołanie zwrotne do his_backend), `ERECEIPT_HIS_URL` (domyślnie `http://localhost:10420/fhir`; w compose `http://his-backend:10420/fhir`), `ERECEIPT_HIS_SERVICE_KEY` (w compose z `HIS_FHIR_SERVICE_KEY`), `ERECEIPT_HIS_CONNECT_TIMEOUT` (`1s`), `ERECEIPT_HIS_READ_TIMEOUT` (`3s`), `ERECEIPT_HIS_SSL_BUNDLE` (puste).

Zmienne `e-laboratory` (`elaboratory.his.*`; compose przekazuje je do kontenera `e-laboratory`): `E_LABORATORY_HIS_ENABLED` (domyślnie `false`; wywołanie zwrotne do his_backend), `E_LABORATORY_HIS_URL` (domyślnie `http://localhost:10420/fhir`; w compose `http://his-backend:10420/fhir`), `E_LABORATORY_HIS_SERVICE_KEY` (w compose z `HIS_FHIR_SERVICE_KEY`), `E_LABORATORY_HIS_CONNECT_TIMEOUT` (`1s`), `E_LABORATORY_HIS_READ_TIMEOUT` (`3s`), `E_LABORATORY_HIS_SSL_BUNDLE` (puste).

Zmienne `e-imaging` (`eimaging.his.*`; compose przekazuje je do kontenera `e-imaging`): `E_IMAGING_HIS_ENABLED` (domyślnie `false`), `E_IMAGING_HIS_URL` (domyślnie `http://localhost:10420/fhir`; w compose `http://his-backend:10420/fhir`), `E_IMAGING_HIS_SERVICE_KEY` (w compose z `HIS_FHIR_SERVICE_KEY`), `E_IMAGING_HIS_CONNECT_TIMEOUT` (`1s`), `E_IMAGING_HIS_READ_TIMEOUT` (`3s`), `E_IMAGING_HIS_SSL_BUNDLE` (puste).

Zmienne tylko dla compose / nakładki (nie czytane przez aplikację): `REGISTRY`, `IMAGE_TAG`, `HIS_BACKEND_IMAGE`, `HIS_FRONTEND_IMAGE`, `E_RECEIPT_IMAGE`, `E_LABORATORY_IMAGE`, `E_IMAGING_IMAGE`, `HIS_DB_NAME`, `HIS_BACKEND_HOST_PORT`/`HIS_BACKEND_CONTAINER_PORT`, `HIS_FRONTEND_HOST_PORT`/`HIS_FRONTEND_CONTAINER_PORT`, `E_RECEIPT_HOST_PORT`/`..._CONTAINER_PORT` (analogicznie `E_LABORATORY_*`, `E_IMAGING_*`), `HIS_DB_HOST_PORT`, `HIS_SNOWSTORM_HOST_PORT`, `HIS_SNOWSTORM_ADMIN_USER` (domyślnie `admin`), `HIS_SNOWSTORM_ADMIN_PASSWORD` (domyślnie `admin`), `COMPOSE_PROFILES`.

Uwagi:

- `--env-file deploy/local.env` służy do **interpolacji** zmiennych w plikach compose; do kontenera `his-backend` trafia lista `environment:` (wszystkie zmienne `HIS_*` z tabeli) oraz `config.env` przez `env_file`. Domyślne wartości `${VAR:-...}` w compose są identyczne z `application.properties` (pusta zmienna środowiskowa nadpisałaby wartość domyślną Springa).
- `HIS_FHIR_SERVICE_KEY`, `HIS_ERECEIPT_ENABLED`, `ERECEIPT_HIS_ENABLED`, `HIS_ELAB_ENABLED`, `E_LABORATORY_HIS_ENABLED`, `HIS_EIMG_ENABLED` i `E_IMAGING_HIS_ENABLED`: lokalnie ustawione w `deploy/local.env.example` (`true` + klucz deweloperski); na produkcji dopisać do `config.env` (klucz taki sam po obu stronach, sekret). Pusty klucz przy włączonej integracji: e-receipt / e-laboratory / e-imaging dostają 401 i pokazują stan synchronizacji `PENDING`.
- `HIS_SNOWSTORM_ENABLED`: domyślnie `false` w aplikacji i w compose; bez zaimportowanego RF2 integracja zwraca 503. `SnowstormProperties` skonstruowane programowo bez wartości (np. w testach jednostkowych) przyjmuje `enabled=true`.
- Wartości przykładowe produkcji: `.env.example` (`HIS_DB_*`, `HIS_LIQUIBASE_CONTEXTS=reference`, `HIS_SNOWSTORM_*`, `HIS_JWT_SECRET`, `HIS_JWT_TTL=8h`; pozostałe zmienne `HIS_*` jako zakomentowane opcje); `deploy/local.env.example` (wartości deweloperskie, `reference,mock`). Na serwerze zmienić `HIS_DB_PASSWORD`, `HIS_JWT_SECRET`, `HIS_SNOWSTORM_ADMIN_PASSWORD`. Istniejący `config.env` na hoście nie jest nadpisywany przez deploy - nowe zmienne dopisać ręcznie.
- Wygenerowanie klucza: `openssl rand -base64 48` (komentarz w `.env.example`).

## Uruchamianie lokalne

Stos w kontenerach (compose + overlay + `deploy/local.env`; pierwsze uruchomienie: skopiować `deploy/local.env.example` do `deploy/local.env`):

```
docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env up -d --build
# opcjonalnie terminologia: dodać --profile terminology
```

Overlay `compose.dev.yml`: budowanie obrazów z źródeł (te same build-args co `.github/steps/create_docker_image.sh`), porty tylko na `127.0.0.1`, dopięcie `postgres`, `e-*` i `snowstorm-lite` do sieci `default`.

**Tryb IDE** (backend z IntelliJ, profil `dev`): uruchamiać tylko `docker compose ... up -d postgres` (opcjonalnie `e-*`, `--profile terminology`); **nie** startować kontenera `his-backend` (konflikt portu 10420). Profil `dev` ma domyślne dane połączenia z `localhost:5432` i klucz JWT. Dev-serwer Angular przekierowuje `/api` i `/ws` na `http://localhost:10420` (`proxy.conf.json`).

Produkcja: `deploy_compose_ssh.sh` kopiuje na host tylko `deploy/compose.yml` (+ `config.env`, `projects.env`), obrazy z rejestru (bez `build:`); host i rejestr w `.github/ci/projects.json`.

Po zakończeniu pracy lokalnej zatrzymać stos (`docker compose ... down`), aby zwolnić porty 5432, 10400, 10420-10423, 8080.

## Health

| Usługa | Endpoint | Dostęp |
| --- | --- | --- |
| `his-backend` | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | publiczny (`permitAll`), bez szczegółów; inne endpointy actuatora niewystawione |
| `e-*` | `/actuator/health/**` | publiczny; `e-receipt`, `e-laboratory` i `e-imaging` dodatkowo `/`, `/ui/**`, `/fhir/**` (bez uwierzytelniania), reszta `denyAll` |
| `his-frontend` | `/` | `wget` w healthchecku kontenera |
