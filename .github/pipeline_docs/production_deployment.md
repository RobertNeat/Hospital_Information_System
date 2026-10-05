# `production_deployment`

## Uruchomienie

- automatycznie po `push` do `main` lub `master`;
- ręcznie przez `workflow_dispatch`, opcjonalnie z pełnym `sha`.

## Cel i kroki

Zapewnia, że na produkcji działa kompletny zestaw aplikacji z jednego commita.
Kroki są wykonywane sekwencyjnie:

1. `context` wybiera i normalizuje pełny SHA;
2. `check` sprawdza wszystkie aplikacje;
3. `build` buduje, skanuje Trivy i publikuje obrazy pod SHA do rejestru
   lokalnego;
4. `deploy` kopiuje Compose/config przez SSH i uruchamia zestaw obrazów o tym
   samym SHA.

## Konfiguracja produkcyjna

Plik `config.env` w katalogu `remote_dir` jest trwałą konfiguracją produkcji.
Przy pierwszym wdrożeniu, jeśli zdalny plik nie istnieje, pipeline kopiuje do
niego plik wskazany przez `deploy.config_file`. Przy kolejnych wdrożeniach
istniejący zdalny `config.env` pozostaje bez zmian i jest używany przez Docker
Compose.
(plik docker_deploy/cloudless-print-bridge/config.env)

Adres hosta, użytkownik, rejestr, pliki i katalog zdalny są w sekcji `deploy`
pliku [`../ci/projects.json`](../ci/projects.json).

## `projects.env`

`deploy/compose.yml` nie zawiera nazw obrazów ani portów na stałe — odwołuje
się do zmiennych `<PROJECT>_IMAGE`, `<PROJECT>_HOST_PORT` i
`<PROJECT>_CONTAINER_PORT`. Plik `deploy_compose_ssh.sh` generuje je z
`projects.json` do `projects.env` i kopiuje na hosta obok `config.env`;
`docker compose` musi być zawsze wywoływany z obydwoma plikami
(`--env-file config.env --env-file projects.env`), inaczej obrazy i porty
pozostaną nierozwiązane.

## Układ Compose (produkcja vs lokalnie)

`deploy/compose.yml` jest kanonicznym plikiem dla WSZYSTKICH usług: `postgres`,
`his-backend`, `his-frontend`, `e-receipt`, `e-laboratory`, `e-imaging` oraz
(profil `terminology`) `snowstorm-lite-init` + `snowstorm-lite`. Nie zawiera
`build:` - deploy kopiuje na host tylko ten plik. Obrazy/porty mają wartości
domyślne `${VAR:-default}` równe wartościom z `projects.json` (nazwy zmiennych
wg `name`: `E_RECEIPT_IMAGE`, `E_RECEIPT_HOST_PORT`, `E_RECEIPT_CONTAINER_PORT` itd.),
a `REGISTRY`/`IMAGE_TAG` domyślnie `local`/`dev`; zgodność defaultów z
`projects.json` sprawdza `validate_projects.sh`. Zmienne `HIS_*` backendu (poza wymaganymi
`HIS_DB_*`) mają wartości domyślne `:-` identyczne z `application.properties`, więc
`docker compose config` działa także z `config.env` bez tych zmiennych.

- Sieci: `his-internal` (`internal: true`) - postgres, backend, e-*, snowstorm-lite;
  `default` - frontend i backend (backend publikuje port, frontend proxy'uje do niego).
  Usługi `e-*` nie publikują portów na hoście (porty z `projects.json` to port FHIR z mTLS).
- Healthchecki: postgres `pg_isready`; backend i `e-*` `curl -fsS http://localhost:<port zarządzania>/actuator/health/readiness`
  (port zarządzania: HTTP bez TLS, nie publikowany; backend 10440, e-* 10441-10443; `curl` doinstalowany
  w runtime `spring.Dockerfile`); frontend `wget` (busybox w nginx:alpine).
  Backend zależy tylko od `postgres: service_healthy` (nie od `e-*` ani Snowstorma).
- Integracja FHIR (recepty, badania laboratoryjne i obrazowe) działa przez mTLS (sekcja "mTLS (FHIR)" niżej):
  `his-backend` wysyła do `https://e-receipt:10421/fhir`, `https://e-laboratory:10422/fhir` i
  `https://e-imaging:10423/fhir`, a `e-*` odsyłają zmianę stanu i wynik na `https://his-backend:10424/fhir`
  (port FHIR backendu; API i `/ws` dla nginx zostają na zwykłym HTTP 10420). Przełączniki (domyślnie
  `false`, ustawić w `config.env`): `HIS_ERECEIPT_ENABLED` / `ERECEIPT_HIS_ENABLED`,
  `HIS_ELAB_ENABLED` / `E_LABORATORY_HIS_ENABLED`, `HIS_EIMG_ENABLED` / `E_IMAGING_HIS_ENABLED`.
- `deploy/compose.dev.yml` - nakładka lokalna (tylko różnice deweloperskie: `build:` z tymi
  samymi build-args co `create_docker_image.sh`, porty na `127.0.0.1`, postgres na `127.0.0.1:5432`,
  Snowstorm na `127.0.0.1:8080`, e-* na `127.0.0.1`: port FHIR i port UI); `deploy/local.env` - lokalne
  wartości (hasła mTLS z `.certs/passwords.env`; plik jest w `.gitignore`).

Lokalnie (z roota repo; `pnpm stack:up` / `stack:down` / `stack:db`):

```
docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env up -d --build
docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env --profile terminology up -d   # + Snowstorm
```

Tryb IDE (backend z IntelliJ, profil `dev`, bez certyfikatów: `HIS_MTLS_ENABLED=false`, ustawia to
`pnpm start:backend`): `... up -d postgres` (opcjonalnie `e-*`, `--profile terminology`) i NIE
uruchamiać kontenera `his-backend` (konflikt portu 10420).
Porty hosta można przesłonić zmiennymi `HIS_DB_HOST_PORT`, `HIS_SNOWSTORM_HOST_PORT`,
`<PROJECT>_HOST_PORT`; jeśli porty 5432/8080 są zajęte, ustaw `HIS_DB_HOST_PORT` /
`HIS_SNOWSTORM_HOST_PORT`. Wolumeny stosu: `hospital-information-system_*`.

Zmienne do ręcznego dopisania w zdalnym `config.env` (istniejący plik nie jest nadpisywany):
`HIS_JWT_SECRET` (WYMAGANE, min. 32 bajty - backend bez niego nie wystartuje; compose przekazuje
je jako `${HIS_JWT_SECRET:-}`), opcjonalnie `HIS_JWT_TTL`, `HIS_JWT_ISSUER`,
`HIS_LOCKOUT_MAX_ATTEMPTS`, `HIS_LOCKOUT_DURATION`, `HIS_CORS_ALLOWED_ORIGINS`,
`HIS_WS_ALLOWED_ORIGINS`, `HIS_LIQUIBASE_CONTEXTS`, `HIS_SNOWSTORM_*`,
`HIS_ERECEIPT_ENABLED`, `ERECEIPT_HIS_ENABLED`, `HIS_ELAB_ENABLED`, `E_LABORATORY_HIS_ENABLED`,
`HIS_EIMG_ENABLED`, `E_IMAGING_HIS_ENABLED`, `COMPOSE_PROFILES=terminology`,
hasła mTLS (WYMAGANE, osiem zmiennych `HIS_BACKEND_*`, `E_RECEIPT_*`, `E_LABORATORY_*`, `E_IMAGING_*`
z końcówką `_KEYSTORE_PASSWORD` / `_TRUSTSTORE_PASSWORD`) i opcjonalnie `HIS_CERTS_DIR`
(wzór: `.env.example`; bez haseł `docker compose config` kończy się błędem).

## mTLS (FHIR)

FHIR między `his-backend` a `e-*` jest zabezpieczony wzajemnym TLS i **domyślnie włączony** (lokalnie i na
produkcji te same certyfikaty; środowisko wewnętrzne). Certyfikat klienta zaufany przez CA `HIS-CA` jest
jedynym uwierzytelnieniem usług `e-*` na `/fhir/**` (plus lista dozwolonych CN `HIS_FHIR_ALLOWED_CLIENT_CNS`
w `his-backend`). Porty: `his-backend` 10420 (API/`/ws`, HTTP), 10424 (FHIR, HTTPS+mTLS), 10440 (zarządzanie);
`e-*` 10421-10423 (FHIR, HTTPS+mTLS), 10431-10433 (UI, HTTP, bez certyfikatu), 10441-10443 (zarządzanie).
Szczegóły i zmienne: `docs/readme/services_pl.md`, `docs/readme/envVariables_pl.md`.

**Generowanie** (CA `HIS-CA`, 4 aplikacje, SAN = nazwa usługi compose + `localhost` + `127.0.0.1`, EKU
`serverAuth`+`clientAuth`, losowe hasła): `scripts/gen-certs.sh [katalog]` (bash, `openssl` i `keytool`;
domyślnie `.certs` w repo, poza gitem). Istniejącej CA skrypt nie nadpisuje; certyfikaty usług i hasła
tworzy za każdym razem od nowa (odnowienie). Wynik: `ca.crt`, `ca.key`, `passwords.env` oraz katalogi
`his_backend`, `e_receipt`, `e_laboratory`, `e_imaging` z `<host>.crt|.key`, `keystore.p12` (alias `his` /
`server`) i `truststore.p12` (CA). Hasła w `passwords.env`: `<KATALOG_UPPER>_KEYSTORE_PASSWORD` i
`<KATALOG_UPPER>_TRUSTSTORE_PASSWORD` (np. `HIS_BACKEND_KEYSTORE_PASSWORD`).

**Wdrożenie na serwer 192.168.1.160** (użytkownik `docker_deploy`, katalog `remote_dir` z `projects.json`:
`/home/docker_deploy/hospital-information-system`). Deploy kopiuje tylko `compose.yml`, więc domyślne
`HIS_CERTS_DIR=../.certs` wskazuje `/home/docker_deploy/.certs` (można ustawić inną ścieżkę bezwzględną
w `config.env`). Na serwer wgrywamy tylko magazyny `.p12` (nie `ca.key`, nie `*.key`):

```
# z maszyny z katalogiem .certs (jednorazowo i po każdym odnowieniu)
for d in his_backend e_receipt e_laboratory e_imaging; do
  ssh docker_deploy@192.168.1.160 "mkdir -p /home/docker_deploy/.certs/$d"
  scp .certs/$d/keystore.p12 .certs/$d/truststore.p12 docker_deploy@192.168.1.160:/home/docker_deploy/.certs/$d/
done
ssh docker_deploy@192.168.1.160 "chmod 755 /home/docker_deploy/.certs /home/docker_deploy/.certs/* && chmod 644 /home/docker_deploy/.certs/*/*.p12"
```

Kontenery działają jako uid 10001, więc pliki `.p12` muszą być czytelne dla innych użytkowników
(magazyny są chronione hasłem); katalogi są montowane tylko do odczytu pod `/certs`. Następnie dopisać do
`config.env` na serwerze osiem haseł z `.certs/passwords.env` (np. `HIS_BACKEND_KEYSTORE_PASSWORD`,
`E_RECEIPT_TRUSTSTORE_PASSWORD`; wartości nigdzie indziej nie zapisywać) i włączyć integrację flagami
`*_ENABLED`. Zmienne aplikacji (`MTLS_KEYSTORE`, `MTLS_TRUSTSTORE`, `MTLS_*_PASSWORD`) compose ustawia sam.

**Wygaśnięcie:** certyfikaty usług są ważne do 2027-10-01 (CA do 2031-09-30). Przed terminem uruchomić
`scripts/gen-certs.sh` (CA zostaje), wgrać nowe `.p12`, zaktualizować hasła w `config.env` i zrestartować
usługi. Po wygaśnięciu połączenia FHIR są odrzucane (e-* pokazują stan `PENDING`).

## PostgreSQL

`deploy/compose.yml` uruchamia usługę `postgres` (`postgres:17-alpine`) z
nazwanym volume `postgres-data` (`/var/lib/postgresql/data`), bez portu na
hoście; łączy się z backendem wyłącznie przez sieć wewnętrzną `his-internal`.
Backend startuje po `service_healthy` bazy. Postgres nie jest aplikacją z
`apps/`, więc **nie** występuje w `projects.json` ani w `validate_projects.sh`.

Zmienne w `config.env` (wzór w `.env.example`): `HIS_DB_NAME`, `HIS_DB_USER`,
`HIS_DB_PASSWORD` (wymagane - `docker compose` kończy się błędem bez nich) oraz
`HIS_LIQUIBASE_CONTEXTS` (domyślnie `reference`; `reference,mock` ładuje dane
demo). Istniejący zdalny `config.env` nie jest nadpisywany — nowe zmienne trzeba
dopisać ręcznie, z własnym hasłem. `POSTGRES_PASSWORD` działa tylko przy
inicjalizacji pustego volume; późniejsza zmiana wymaga `ALTER USER`.

Backup: `docker compose --env-file config.env --env-file projects.env exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > his.sql`.
`docker compose down -v` usuwa volume z danymi — nie używać na produkcji.

Lokalny dev: `docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env up -d postgres`
(Postgres na `127.0.0.1:5432`, baza/użytkownik/hasło `his`, volume `postgres-data`).

## Terminologia SNOMED CT (Snowstorm Lite)

`deploy/compose.yml` zawiera opcjonalną usługę `snowstorm-lite`
(`snomedinternational/snowstorm-lite:2.7.0`, tag przypięty) w profilu compose
`terminology` — **nie startuje domyślnie**. Usługa: nazwany volume
`snowstorm-lite-data` (`/app/lucene-index`, indeks Lucene), sieć `his-internal`,
**brak portu na hoście**, `restart: unless-stopped`, healthcheck na
`/fhir/metadata`. Nie potrzebuje internetu. Pomocniczy, jednorazowy
`snowstorm-lite-init` nadaje uid 1000 woluminowi (obraz działa nie-rootem).
Backend ma na stałe `HIS_SNOWSTORM_URL=http://snowstorm-lite:8080/fhir`, a
`HIS_SNOWSTORM_ENABLED` z `config.env` (domyślnie `false`); **nie** ma
`depends_on` na Snowstorma i działa bez niego.

### Uruchomienie profilu

`deploy_compose_ssh.sh` wywołuje `docker compose` bez `--profile`, więc profil
włącza się bez zmiany skryptu przez zmienną w zdalnym `config.env`:
`COMPOSE_PROFILES=terminology` (Compose czyta ją z `--env-file`; sprawdzone).
Bez niej usługa nie ruszy. Hasło: zmienna `HIS_SNOWSTORM_ADMIN_PASSWORD` ma
domyślne `admin` (`:-`, a nie `:?`, bo Compose interpoluje zmienne także
nieaktywnych profili) — na serwerze ustawić własne.

### Zmienne do ręcznego dopisania w zdalnym `config.env`

```
COMPOSE_PROFILES=terminology
HIS_SNOWSTORM_ENABLED=false        # true po zaimportowaniu RF2
HIS_SNOWSTORM_ADMIN_USER=admin
HIS_SNOWSTORM_ADMIN_PASSWORD=<własne hasło>
```

Uwaga: `config.env` trafia też do backendu (`env_file`), więc hasło admina Lite
jest widoczne w jego środowisku.

### Import RF2 (ręcznie)

Paczek RF2 **nie ma w repozytorium** (licencja). Wymagana ważna licencja SNOMED CT
(afiliacja MLDS / krajowy ośrodek, np. dla Polski — CSIOZ/CeZ). Jedna edycja
naraz (kolejny import zastępuje poprzedni); import edycji międzynarodowej
trwa ok. 5 min i potrzebuje ok. 1–1,5 GB RAM (później ok. 500 MB; obraz startuje
z `-Xms1g -Xmx4g`). Usługa nie ma portu na hoście, więc import z serwera przez
`docker compose exec`/kontener z tej samej sieci, np. po skopiowaniu zipa na serwer:

```
docker compose --env-file config.env --env-file projects.env cp SnomedCT_*.zip snowstorm-lite:/tmp/rf2.zip
docker compose --env-file config.env --env-file projects.env exec snowstorm-lite \
  curl -u admin:HASLO --form file=@/tmp/rf2.zip \
  --form version-uri="http://snomed.info/sct/900000000000207008/version/YYYYMMDD" \
  http://localhost:8080/fhir-admin/load-package
```

(lokalnie w dev, z portem `127.0.0.1:8080`: `curl -u admin:admin --form file=@SnomedCT_*.zip --form version-uri="..." http://localhost:8080/fhir-admin/load-package`).
Po imporcie ustawić `HIS_SNOWSTORM_ENABLED=true` i `docker compose ... up -d`.

### Backup i dev

Backup indeksu: zatrzymać usługę i zarchiwizować volume, np.
`docker run --rm -v hospital-information-system_snowstorm-lite-data:/d -v "$PWD":/b alpine tar czf /b/snowstorm-lite.tgz -C /d .`
(alternatywa: ponowny import RF2). `docker compose down -v` kasuje indeks.
Dev: `docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env --profile terminology up -d`
(Lite na `127.0.0.1:8080`, admin/`admin`).

## Uwierzytelnianie i konta proste

Backend używa tokenów JWT (HS256, prawie bezstanowo): frontend loguje się przez `POST /api/v1/auth/login`, domyślnie
trzyma token w `localStorage` (przeżywa przeładowanie strony; `AuthService.restoreSession()` weryfikuje go przy
starcie przez `GET /auth/me`) i wysyła `Authorization: Bearer ...`; wylogowanie = usunięcie tokenu. Token ma krótkie
TTL (`HIS_JWT_TTL`, domyślnie 15m) i jest odświeżany proaktywnie przez `POST /api/v1/auth/refresh` przed
wygaśnięciem; każde konto ma w bazie licznik `token_version` zapisany w tokenie - zablokowanie konta lub zmiana
roli bumpuje licznik i natychmiast unieważnia już wydane tokeny (sprawdzane przy każdym żądaniu i okresowo dla
połączeń STOMP), bez czekania na `exp`.

Zmienne w zdalnym `config.env` (istniejący plik nie jest nadpisywany - dopisać ręcznie):

- `HIS_JWT_SECRET` - WYMAGANE, min. 32 bajty (np. `openssl rand -base64 48`). Bez niego backend nie startuje.
  Losowe klucze JWT generuje `python utilities/secret_generator.py`.
  Zmiana klucza unieważnia wszystkie wystawione tokeny.
- `HIS_JWT_TTL` - opcjonalnie (domyślnie `15m`).
- `HIS_PERSIST_SESSION` - zmienna *build-time* frontendu (nie backendu; ustawiana przy `pnpm build`, nie w
  `config.env`), domyślnie `true`. Ustawienie na `false` przebudowuje frontend tak, by trzymał token tylko w pamięci
  (sesja kończy się przy każdym przeładowaniu strony).

### Konta proste (login = hasło) - ryzyko

Migracja `db/changelog/demo/001-demo-accounts.sql` (kontekst Liquibase `reference`, więc ładuje się także na
produkcji) tworzy oddział `DEMO` i konta w statusie `active`, o haśle równym loginowi:

| login / hasło | rola |
| --- | --- |
| `admin` | admin |
| `user`, `doctor` | doctor |
| `nurse` | nurse |
| `lab-tech` | lab_technician |
| `radiologist` | radiologist |
| `pharmacist` | pharmacist |
| `registrar` | registrar |

Ryzyko: hasła są trywialne i publiczne (w repozytorium), a konto `admin` ma pełne uprawnienia administracyjne.
Każdy, kto ma dostęp sieciowy do hosta (np. w LAN), może się zalogować. Zalecenia: nie wystawiać aplikacji poza
zaufaną sieć; po wdrożeniu zablokować konta (`POST /api/v1/staff/{id}/lock` jako admin) lub zmienić hasła
w bazie (hash BCrypt cost 10, bez prefiksu `{bcrypt}`) przed użyciem z prawdziwymi danymi.

## Proxy frontend → backend

`apps/his_frontend/nginx.conf` proxy'uje `/api/` i `/ws` (z nagłówkami
Upgrade/Connection dla WebSocket) na `http://his-backend:10420` (port zaszyty na
stałe — zmiana `ports.container` backendu wymaga edycji `nginx.conf`). Adres
jest w zmiennej z `resolver 127.0.0.11` (Docker DNS), więc nginx startuje także,
gdy backend jeszcze nie działa. Dev: `apps/his_frontend/proxy.conf.json`
(`/api`, `/ws` → `http://localhost:10420`) podpięty w `angular.json`.
