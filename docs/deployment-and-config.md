# Wdrożenie i konfiguracja

Usługi compose, porty, sieci, zmienne środowiskowe `HIS_*` i uruchamianie lokalne. 

Źródła: `deploy/compose.yml`, `deploy/compose.dev.yml`, `deploy/local.env(.example)`, `.env.example`, `.github/ci/projects.json`, `apps/his_backend/src/main/resources/application*.properties`, `apps/his_frontend/nginx.conf`, `apps/his_frontend/proxy.conf.json`. Procedury produkcyjne: `.github/pipeline_docs/production_deployment.md`.

## Usługi i porty

Porty z `.github/ci/projects.json` (host = kontener):

| Usługa (compose) | Obraz | Port | Publikacja na hoście | Sieci | Uwagi |
| --- | --- | --- | --- | --- | --- |
| `postgres` | `postgres:17-alpine` | 5432 | tylko overlay dev: `127.0.0.1:${HIS_DB_HOST_PORT:-5432}` | `his-internal` (+ `default` w dev) | wolumen `postgres-data`; healthcheck `pg_isready` |
| `his-backend` | `${REGISTRY}/${HIS_BACKEND_IMAGE}:${IMAGE_TAG}` | **10420** (API, `/ws`; HTTP), **10424** (FHIR; HTTPS, mTLS), **10440** (zarządzanie; HTTP) | prod: `0.0.0.0:${HIS_BACKEND_HOST_PORT:-10420}`; dev: `127.0.0.1:...` i `127.0.0.1:10424`; 10440 nie jest publikowany | `default` + `his-internal` | `depends_on: postgres (healthy)`; certyfikaty `${HIS_CERTS_DIR}/his_backend` -> `/certs` (ro); healthcheck `curl http://localhost:10440/actuator/health/readiness` (start_period 60 s) |
| `his-frontend` | `.../his-frontend` | **10400** | prod: `0.0.0.0:${HIS_FRONTEND_HOST_PORT:-10400}`; dev: `127.0.0.1:...` | `default` | nginx; proxy `/api/` i `/ws` do `http://his-backend:10420` (Docker DNS `127.0.0.11`, host rozwiązywany per żądanie) |
| `e-receipt` | `.../e-receipt` | **10421** (FHIR; HTTPS, mTLS), **10431** (UI; HTTP), **10441** (zarządzanie; HTTP) | tylko overlay dev (loopback): 10421 i 10431; 10441 nie jest publikowany | `his-internal` (+ `default` w dev) | UI Thymeleaf `/ui/prescriptions`, FHIR `/fhir/MedicationRequest`; certyfikaty `${HIS_CERTS_DIR}/e_receipt` -> `/certs` (ro); healthcheck na porcie zarządzania |
| `e-laboratory` | `.../e-laboratory` | **10422** (FHIR; HTTPS, mTLS), **10432** (UI; HTTP), **10442** (zarządzanie; HTTP) | tylko overlay dev (loopback): 10422 i 10432 | j.w. | UI Thymeleaf `/ui/orders`, FHIR `/fhir/ServiceRequest`; certyfikaty `${HIS_CERTS_DIR}/e_laboratory`; healthcheck na porcie zarządzania |
| `e-imaging` | `.../e-imaging` | **10423** (FHIR; HTTPS, mTLS), **10433** (UI; HTTP), **10443** (zarządzanie; HTTP) | tylko overlay dev (loopback): 10423 i 10433 | j.w. | UI Thymeleaf `/ui/orders`, FHIR `/fhir/ServiceRequest`; certyfikaty `${HIS_CERTS_DIR}/e_imaging`; healthcheck na porcie zarządzania |
| `snowstorm-lite-init` | `snomedinternational/snowstorm-lite:2.7.0` | - | - | `network_mode: none` | profil `terminology`; jednorazowo ustawia właściciela wolumenu (`chown 1000:1000`) |
| `snowstorm-lite` | `snomedinternational/snowstorm-lite:2.7.0` | 8080 | tylko overlay dev: `127.0.0.1:${HIS_SNOWSTORM_HOST_PORT:-8080}` | `his-internal` (+ `default` w dev) | profil **`terminology`**; wolumen `snowstorm-lite-data`; healthcheck `GET /fhir/metadata`; hasło admina `HIS_SNOWSTORM_ADMIN_PASSWORD` |

Sieci: `his-internal` (`internal: true`, bez dostępu na zewnątrz) łączy backend z bazą, usługami `e-*` i Snowstorm; `default` służy do publikacji portów. Backend **nie ma `depends_on`** od `snowstorm-lite` (terminologia opcjonalna).

Obrazy budowane z `.github/docker/spring.Dockerfile` (backend, `e-*`) i `.github/docker/angular.Dockerfile` (frontend); wersje runtime: Java 25, Node 22.

## Usługi `e-*`

`e-receipt` (FHIR 10421, UI 10431) jest symulatorem e-recept: FHIR R4 `POST/GET/PUT /fhir/MedicationRequest[/{eRxKey}]` (HAPI), UI Thymeleaf pod `/` -> `/ui/prescriptions` (lista recept, zmiana stanu `partially_dispensed`/`dispensed`/`cancelled`/`expired`, ponowienie wysyłki do HIS) oraz `/actuator/health/**`. Aplikacyjnie wszystko to jest publiczne (bez uwierzytelniania), reszta `denyAll` (`SecurityConfig`); dostęp rozdziela transport (sekcja `mtls` niżej): FHIR tylko przez HTTPS z certyfikatem klienta, UI i health bez certyfikatu na osobnych portach HTTP. Stan recept trzymany jest **w pamięci** (brak bazy; restart czyści listę, a HIS pozostaje autorytatywny). Zmiana stanu z UI jest najpierw wysyłana do HIS (`PUT {his}/fhir/MedicationRequest/{id}` przez mTLS): odrzucenie przez HIS (4xx) blokuje zmianę, niedostępność HIS zostawia zmianę lokalną ze stanem synchronizacji `PENDING` (przycisk ponowienia). Kod: pakiety `fhir` i integracji w `his_backend`.

`e-laboratory` (FHIR 10422, UI 10432) jest symulatorem laboratorium: FHIR R4 `POST/GET/PUT /fhir/ServiceRequest[/{id zlecenia HIS}]` (HAPI), UI Thymeleaf pod `/` -> `/ui/orders` (lista zleceń, zmiana stanu `scheduled`/`specimen_collected`/`in_progress`/`completed`/`cancelled`, formularz wyniku z obserwacjami dla badań zlecenia, ponowienie wysyłki stanu i wyniku do HIS) oraz `/actuator/health/**`; publiczne bez uwierzytelniania aplikacyjnego, reszta `denyAll` (`SecurityConfig`). Stan zleceń i wyników trzymany jest **w pamięci** (restart czyści listę, HIS jest autorytatywny). Zmiana stanu z UI jest najpierw wysyłana do HIS (`PUT {his}/fhir/ServiceRequest/{id}`), wynik jako `POST {his}/fhir/DiagnosticReport` (oba przez mTLS): odrzucenie przez HIS (4xx) blokuje zmianę, niedostępność HIS zostawia ją lokalnie ze stanem synchronizacji `PENDING` (przyciski ponowienia). Gdy każda pozycja ma zatwierdzony i przekazany wynik, zlecenie przechodzi lokalnie do `completed` (jak auto-`completed` w HIS). Kod: pakiety `fhir` i integracji w `his_backend`.

`e-imaging` (FHIR 10423, UI 10433) jest symulatorem pracowni obrazowej: FHIR R4 `POST/GET/PUT /fhir/ServiceRequest[/{id zlecenia HIS}]` (HAPI), UI Thymeleaf pod `/` -> `/ui/orders` (lista zleceń z badaniem, modalnością, lateralnością, kontrastem, wskazaniem, pilnością i terminem; zmiana stanu `ordered`/`scheduled`/`in_progress`/`completed`/`cancelled`; formularz wyniku: opis, wnioski, flaga krytyczny, radiolog; ponowienie wysyłki stanu i wyniku do HIS) oraz `/actuator/health/**`; publiczne bez uwierzytelniania aplikacyjnego, reszta `denyAll` (`SecurityConfig`). Stan zleceń i wyników trzymany jest **w pamięci** (restart czyści listę, HIS jest autorytatywny); stan początkowy zlecenia pochodzi z HIS (zlecenie ze slotem przychodzi jako `scheduled`). Zmiana stanu z UI jest najpierw wysyłana do HIS (`PUT {his}/fhir/ServiceRequest/{id}`), wynik jako `POST {his}/fhir/DiagnosticReport` (oba przez mTLS): odrzucenie przez HIS (4xx) blokuje zmianę, niedostępność HIS zostawia ją lokalnie ze stanem synchronizacji `PENDING` (przyciski ponowienia). Formularz wyniku jest dostępny w stanach `scheduled` i `in_progress`; wynik `final` przekazany do HIS przenosi zlecenie lokalnie do `completed` (jak auto-`completed` w HIS). Kod: pakiety `fhir` i integracji w `his_backend`.

`his-backend` wywołuje `e-receipt` tylko gdy `HIS_ERECEIPT_ENABLED=true`, `e-laboratory` tylko gdy `HIS_ELAB_ENABLED=true`, a `e-imaging` tylko gdy `HIS_EIMG_ENABLED=true` (wszystkie domyślnie `false`); `his-backend` nie ma `depends_on` od `e-*`.

### mTLS (FHIR) - domyślnie włączony

Między `his-backend` a `e-*` FHIR działa przez mTLS; certyfikaty (CA `HIS-CA`, SAN: nazwa usługi compose + `localhost` + `127.0.0.1`, EKU `serverAuth`+`clientAuth`, ważne do 2027-10-01) powstają w `.certs/` (poza repo) przez `scripts/gen-certs.sh` (opis w `.github/pipeline_docs/production_deployment.md`). Autoryzacja klienta = certyfikat zaufany przez truststore; `his-backend` dodatkowo sprawdza CN klienta (`HIS_FHIR_ALLOWED_CLIENT_CNS`, domyślnie `e-receipt,e-laboratory,e-imaging`).

Mechanizm: `application.properties` zawiera `spring.profiles.include=mtls-${HIS_MTLS_ENABLED:true}` i grupę `mtls-true=mtls`, więc profil `mtls` (`application-mtls.properties` w każdej aplikacji) jest aktywny domyślnie. Wyłączenie (testy, tryb IDE bez certyfikatów): `HIS_MTLS_ENABLED=false` (zmienna środowiskowa lub `-DHIS_MTLS_ENABLED=false`); testy jednostkowe/integracyjne ustawiają to w `src/test/resources/config/application.properties`. Paczki SSL ładują się przy starcie, więc brak plików lub haseł przerywa start aplikacji.

Rozdział portów (klasa `MtlsConnectors` w każdej aplikacji, rozróżnienie po `request.isSecure()`, nagłówki `X-Forwarded-*` nie są respektowane):

| Aplikacja | Port HTTPS (mTLS, `client-auth=need`) | Port HTTP | Port zarządzania (HTTP, bez TLS) |
| --- | --- | --- | --- |
| `his-backend` | `server.port` = **10424** (`HIS_FHIR_PORT`), tylko `/fhir/**` | **10420**: `/api/**`, `/ws`, bez `/fhir/**` (przeglądarka przez nginx) | **10440** (`HIS_MANAGEMENT_PORT`) |
| `e-receipt` / `e-laboratory` / `e-imaging` | `server.port` = **10421** / **10422** / **10423**, tylko `/fhir/**` | **10431** / **10432** / **10433** (`*_UI_PORT`): tylko `/` i `/ui/**` (UI dostępne z przeglądarki bez certyfikatu), bez `/fhir/**` | **10441** / **10442** / **10443** (`*_MANAGEMENT_PORT`) |

Żądanie niezgodne z portem (np. `/fhir/**` na HTTP, `/api/**` na HTTPS, `/ui/**` na HTTPS) dostaje 404. Port zarządzania (`management.server.port`, `management.server.ssl.enabled=false`) serwuje wyłącznie `/actuator/health/**` i nie jest publikowany na host; healthchecki compose używają tego portu. Bez mTLS (`HIS_MTLS_ENABLED=false`) wszystko jest na jednym porcie aplikacji (10420-10423) i zarządzania nie ma osobnego portu; `/fhir/**` w `his-backend` odrzuca wtedy każde żądanie (401), a `e-*` wystawiają FHIR bez uwierzytelniania (wyłącznie tryb testowy/IDE).

Klienty FHIR: `his.integration.{ereceipt,elab,eimg}.ssl-bundle` (HIS; `HIS_ERECEIPT_SSL_BUNDLE`, `HIS_ELAB_SSL_BUNDLE`, `HIS_EIMG_SSL_BUNDLE`) oraz `ereceipt.his.ssl-bundle`, `elaboratory.his.ssl-bundle`, `eimaging.his.ssl-bundle` (e-*; `ERECEIPT_HIS_SSL_BUNDLE`, `E_LABORATORY_HIS_SSL_BUNDLE`, `E_IMAGING_HIS_SSL_BUNDLE`) wskazują paczkę `spring.ssl.bundle.*` (profil `mtls` ustawia `mtls`) i są przekazywane do `HttpClientSettings.withSslBundle`; adresy mają schemat `https://`. Zmienne paczki w każdej aplikacji: `MTLS_KEYSTORE`, `MTLS_KEYSTORE_PASSWORD` (wymagane), `MTLS_TRUSTSTORE`, `MTLS_TRUSTSTORE_PASSWORD` (wymagane), `MTLS_KEY_ALIAS` (domyślnie `his` w `his-backend`, `server` w `e-*`); domyślne ścieżki `file:./certs/{keystore,truststore}.p12`, w compose `file:/certs/...` (montowanie `${HIS_CERTS_DIR}/<usługa>` -> `/certs` tylko do odczytu, `HIS_CERTS_DIR` domyślnie `../.certs` względem `deploy/`). Hasła w compose: `HIS_BACKEND_*_PASSWORD`, `E_RECEIPT_*_PASSWORD`, `E_LABORATORY_*_PASSWORD`, `E_IMAGING_*_PASSWORD` (`*_KEYSTORE_PASSWORD` i `*_TRUSTSTORE_PASSWORD`), wymagane (`:?`) w `compose.yml`.

Testy: `MtlsFhirE2ETest` (`his_backend`) i `MtlsE2ETest` (`e-receipt`) uruchamiają aplikację na prawdziwych konektorach z certyfikatami TEST-ONLY generowanymi w teście (`keytool` z JDK, klasa `TestPki`; bez plików z `.certs`): certyfikat klienta zaufanego CA przechodzi, brak certyfikatu i certyfikat obcego CA są odrzucane na poziomie TLS, a rozdział `/fhir` / `/api` / `/ui` i port zarządzania są sprawdzane osobno.

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
| `HIS_MTLS_ENABLED` | włącza mTLS (profil `mtls`); `false` = tryb bez certyfikatów (testy, IDE) | `true` | - | `${HIS_MTLS_ENABLED:-true}` | nie |
| `HIS_FHIR_PORT` | port HTTPS (mTLS) dla `/fhir/**` | `10424` | - | domyślny | nie |
| `HIS_MANAGEMENT_PORT` | port zarządzania (HTTP, `/actuator/health/**`) | `10440` | - | `${HIS_BACKEND_MANAGEMENT_PORT:-10440}` | nie |
| `HIS_FHIR_ALLOWED_CLIENT_CNS` | CN certyfikatów klienta dozwolonych na `/fhir/**` (po przecinku) | `e-receipt,e-laboratory,e-imaging` | - | domyślny | nie |
| `HIS_ERECEIPT_ENABLED` | włącza klienta e-receipt (wysyłka recept `e_prescription`, anulowanie) | `false` | - | `${HIS_ERECEIPT_ENABLED:-false}` | nie |
| `HIS_ERECEIPT_URL` | baza FHIR e-receipt | `https://localhost:10421/fhir` | - | `https://e-receipt:10421/fhir` (na sztywno) | nie |
| `HIS_ERECEIPT_CONNECT_TIMEOUT` | timeout połączenia | `1s` | - | `${HIS_ERECEIPT_CONNECT_TIMEOUT:-1s}` | nie |
| `HIS_ERECEIPT_READ_TIMEOUT` | timeout odczytu | `3s` | - | `${HIS_ERECEIPT_READ_TIMEOUT:-3s}` | nie |
| `HIS_ERECEIPT_SSL_BUNDLE` | nazwa paczki `spring.ssl.bundle.*` klienta (puste = bez SSL bundle) | `mtls` (profil `mtls`) | - | nie | nie |
| `HIS_ELAB_ENABLED` | włącza klienta e-laboratory (wysyłka zleceń lab, anulowanie) | `false` | - | `${HIS_ELAB_ENABLED:-false}` | nie |
| `HIS_ELAB_URL` | baza FHIR e-laboratory | `https://localhost:10422/fhir` | - | `https://e-laboratory:10422/fhir` (na sztywno) | nie |
| `HIS_ELAB_CONNECT_TIMEOUT` | timeout połączenia | `1s` | - | `${HIS_ELAB_CONNECT_TIMEOUT:-1s}` | nie |
| `HIS_ELAB_READ_TIMEOUT` | timeout odczytu | `3s` | - | `${HIS_ELAB_READ_TIMEOUT:-3s}` | nie |
| `HIS_ELAB_SSL_BUNDLE` | nazwa paczki `spring.ssl.bundle.*` klienta (puste = bez SSL bundle) | `mtls` (profil `mtls`) | - | nie | nie |
| `HIS_EIMG_ENABLED` | włącza klienta e-imaging (wysyłka zleceń obrazowych, anulowanie) | `false` | - | `${HIS_EIMG_ENABLED:-false}` | nie |
| `HIS_EIMG_URL` | baza FHIR e-imaging | `https://localhost:10423/fhir` | - | `https://e-imaging:10423/fhir` (na sztywno) | nie |
| `HIS_EIMG_CONNECT_TIMEOUT` | timeout połączenia | `1s` | - | `${HIS_EIMG_CONNECT_TIMEOUT:-1s}` | nie |
| `HIS_EIMG_READ_TIMEOUT` | timeout odczytu | `3s` | - | `${HIS_EIMG_READ_TIMEOUT:-3s}` | nie |
| `HIS_EIMG_SSL_BUNDLE` | nazwa paczki `spring.ssl.bundle.*` klienta (puste = bez SSL bundle) | `mtls` (profil `mtls`) | - | nie | nie |
| `MTLS_*` | paczka SSL profilu `mtls` (`MTLS_KEYSTORE`, `MTLS_KEYSTORE_PASSWORD`, `MTLS_TRUSTSTORE`, `MTLS_TRUSTSTORE_PASSWORD`, `MTLS_KEY_ALIAS`) | ścieżki `file:./certs/...`, hasła wymagane | - | `file:/certs/...`, hasła z `HIS_BACKEND_*_PASSWORD` | tak (hasła; bez nich start się nie uda) |

Stałe ustawienia (bez zmiennej): `server.port=10420` (bez mTLS; z mTLS `10424` + HTTP `10420`), paginacja domyślnie 20 / maks. 100, `management.endpoints.web.exposure.include=health` (probes włączone, brak szczegółów), `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`.

Zmienne `e-receipt` (`ereceipt.his.*`; compose przekazuje je do kontenera `e-receipt`): `ERECEIPT_HIS_ENABLED` (domyślnie `false`; wywołanie zwrotne do his_backend), `ERECEIPT_HIS_URL` (domyślnie `https://localhost:10424/fhir`; w compose `https://his-backend:10424/fhir`), `ERECEIPT_HIS_CONNECT_TIMEOUT` (`1s`), `ERECEIPT_HIS_READ_TIMEOUT` (`3s`), `ERECEIPT_HIS_SSL_BUNDLE` (domyślnie `mtls`), `ERECEIPT_UI_PORT` (port UI HTTP), `ERECEIPT_MANAGEMENT_PORT` (port zarządzania).

Zmienne `e-laboratory` (`elaboratory.his.*`; compose przekazuje je do kontenera `e-laboratory`): `E_LABORATORY_HIS_ENABLED` (domyślnie `false`; wywołanie zwrotne do his_backend), `E_LABORATORY_HIS_URL` (domyślnie `https://localhost:10424/fhir`; w compose `https://his-backend:10424/fhir`), `E_LABORATORY_HIS_CONNECT_TIMEOUT` (`1s`), `E_LABORATORY_HIS_READ_TIMEOUT` (`3s`), `E_LABORATORY_HIS_SSL_BUNDLE` (domyślnie `mtls`), `E_LABORATORY_UI_PORT` (port UI HTTP), `E_LABORATORY_MANAGEMENT_PORT` (port zarządzania).

Zmienne `e-imaging` (`eimaging.his.*`; compose przekazuje je do kontenera `e-imaging`): `E_IMAGING_HIS_ENABLED` (domyślnie `false`), `E_IMAGING_HIS_URL` (domyślnie `https://localhost:10424/fhir`; w compose `https://his-backend:10424/fhir`), `E_IMAGING_HIS_CONNECT_TIMEOUT` (`1s`), `E_IMAGING_HIS_READ_TIMEOUT` (`3s`), `E_IMAGING_HIS_SSL_BUNDLE` (domyślnie `mtls`), `E_IMAGING_UI_PORT` (port UI HTTP), `E_IMAGING_MANAGEMENT_PORT` (port zarządzania).

Zmienne tylko dla compose / nakładki (nie czytane przez aplikację): `REGISTRY`, `IMAGE_TAG`, `HIS_BACKEND_IMAGE`, `HIS_FRONTEND_IMAGE`, `E_RECEIPT_IMAGE`, `E_LABORATORY_IMAGE`, `E_IMAGING_IMAGE`, `HIS_DB_NAME`, `HIS_BACKEND_HOST_PORT`/`HIS_BACKEND_CONTAINER_PORT`, `HIS_FRONTEND_HOST_PORT`/`HIS_FRONTEND_CONTAINER_PORT`, `E_RECEIPT_HOST_PORT`/`..._CONTAINER_PORT` (analogicznie `E_LABORATORY_*`, `E_IMAGING_*`; port FHIR z mTLS), `E_RECEIPT_UI_PORT`/`E_RECEIPT_UI_HOST_PORT` i `E_RECEIPT_MANAGEMENT_PORT` (analogicznie dla pozostałych e-*), `HIS_BACKEND_MANAGEMENT_PORT`, `HIS_BACKEND_FHIR_HOST_PORT` (nakładka dev), `HIS_CERTS_DIR` (domyślnie `../.certs`), hasła mTLS `HIS_BACKEND_`/`E_RECEIPT_`/`E_LABORATORY_`/`E_IMAGING_` + `KEYSTORE_PASSWORD`/`TRUSTSTORE_PASSWORD`, `HIS_DB_HOST_PORT`, `HIS_SNOWSTORM_HOST_PORT`, `HIS_SNOWSTORM_ADMIN_USER` (domyślnie `admin`), `HIS_SNOWSTORM_ADMIN_PASSWORD` (domyślnie `admin`), `COMPOSE_PROFILES`.

Uwagi:

- `--env-file deploy/local.env` służy do **interpolacji** zmiennych w plikach compose; do kontenera `his-backend` trafia lista `environment:` (wszystkie zmienne `HIS_*` z tabeli) oraz `config.env` przez `env_file`. Domyślne wartości `${VAR:-...}` w compose są identyczne z `application.properties` (pusta zmienna środowiskowa nadpisałaby wartość domyślną Springa).
- `HIS_ERECEIPT_ENABLED`, `ERECEIPT_HIS_ENABLED`, `HIS_ELAB_ENABLED`, `E_LABORATORY_HIS_ENABLED`, `HIS_EIMG_ENABLED` i `E_IMAGING_HIS_ENABLED`: lokalnie ustawione w `deploy/local.env.example` (`true`); na produkcji dopisać do `config.env`. Hasła mTLS (osiem zmiennych `*_KEYSTORE_PASSWORD`/`*_TRUSTSTORE_PASSWORD`) są wymagane w `deploy/local.env` i `config.env` (wartości z `.certs/passwords.env`); certyfikat spoza zaufanego CA lub z CN spoza listy daje błąd połączenia / 401, a e-* pokazują stan synchronizacji `PENDING`.
- `HIS_SNOWSTORM_ENABLED`: domyślnie `false` w aplikacji i w compose; bez zaimportowanego RF2 integracja zwraca 503. `SnowstormProperties` skonstruowane programowo bez wartości (np. w testach jednostkowych) przyjmuje `enabled=true`.
- Wartości przykładowe produkcji: `.env.example` (`HIS_DB_*`, `HIS_LIQUIBASE_CONTEXTS=reference`, `HIS_SNOWSTORM_*`, `HIS_JWT_SECRET`, `HIS_JWT_TTL=8h`; pozostałe zmienne `HIS_*` jako zakomentowane opcje); `deploy/local.env.example` (wartości deweloperskie, `reference,mock`). Na serwerze zmienić `HIS_DB_PASSWORD`, `HIS_JWT_SECRET`, `HIS_SNOWSTORM_ADMIN_PASSWORD` i ustawić hasła mTLS. Istniejący `config.env` na hoście nie jest nadpisywany przez deploy - nowe zmienne dopisać ręcznie.
- Wygenerowanie klucza: `openssl rand -base64 48` (komentarz w `.env.example`).

## Uruchamianie lokalne

Stos w kontenerach (compose + overlay + `deploy/local.env`; pierwsze uruchomienie: skopiować `deploy/local.env.example` do `deploy/local.env`, wpisać hasła z `.certs/passwords.env`; certyfikaty: `scripts/gen-certs.sh`):

```
docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env up -d --build
# opcjonalnie terminologia: dodać --profile terminology
```

Overlay `compose.dev.yml`: budowanie obrazów z źródeł (te same build-args co `.github/steps/create_docker_image.sh`), porty tylko na `127.0.0.1`, dopięcie `postgres`, `e-*` i `snowstorm-lite` do sieci `default`.

**Tryb IDE** (backend z IntelliJ, profil `dev`, bez certyfikatów: `HIS_MTLS_ENABLED=false`, skrypt `pnpm start:backend` ustawia to sam): uruchamiać tylko `docker compose ... up -d postgres` (opcjonalnie `e-*`, `--profile terminology`); **nie** startować kontenera `his-backend` (konflikt portu 10420). Profil `dev` ma domyślne dane połączenia z `localhost:5432` i klucz JWT. W trybie bez mTLS `/fhir/**` w `his-backend` jest nieczynne, więc integracja z `e-*` wymaga mTLS (certyfikaty z `.certs`, `MTLS_*`). Dev-serwer Angular przekierowuje `/api` i `/ws` na `http://localhost:10420` (`proxy.conf.json`).

Produkcja: `deploy_compose_ssh.sh` kopiuje na host tylko `deploy/compose.yml` (+ `config.env`, `projects.env`), obrazy z rejestru (bez `build:`); host i rejestr w `.github/ci/projects.json`.

Po zakończeniu pracy lokalnej zatrzymać stos (`docker compose ... down`), aby zwolnić porty 5432, 10400, 10420-10424, 10431-10433, 8080.

## Health

| Usługa | Endpoint | Dostęp |
| --- | --- | --- |
| `his-backend` | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` na porcie zarządzania 10440 | publiczny (`permitAll`), bez szczegółów; inne endpointy actuatora niewystawione; port nie jest publikowany |
| `e-*` | `/actuator/health/**` na porcie zarządzania (10441-10443) | publiczny; `e-receipt`, `e-laboratory` i `e-imaging` dodatkowo `/`, `/ui/**` (port UI, HTTP) i `/fhir/**` (port aplikacji, mTLS), bez uwierzytelniania aplikacyjnego, reszta `denyAll` |
| `his-frontend` | `/` | `wget` w healthchecku kontenera |
