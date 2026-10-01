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
  Usługi `e-*` nie publikują portów na hoście (porty z `projects.json` to rezerwacja).
- Healthchecki: postgres `pg_isready`; backend i `e-*` `curl -fsS .../actuator/health/readiness`
  (`curl` doinstalowany w runtime `spring.Dockerfile`); frontend `wget` (busybox w nginx:alpine).
  Backend zależy tylko od `postgres: service_healthy` (nie od `e-*` ani Snowstorma).
- `deploy/compose.dev.yml` - nakładka lokalna (tylko różnice deweloperskie: `build:` z tymi
  samymi build-args co `create_docker_image.sh`, porty na `127.0.0.1`, postgres na `127.0.0.1:5432`,
  Snowstorm na `127.0.0.1:8080`); `deploy/local.env` - lokalne wartości (bez sekretów).

Lokalnie (z roota repo; `pnpm stack:up` / `stack:down` / `stack:db`):

```
docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env up -d --build
docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env --profile terminology up -d   # + Snowstorm
```

Tryb IDE (backend z IntelliJ, profil `dev`): `... up -d postgres` (opcjonalnie `e-*`,
`--profile terminology`) i NIE uruchamiać kontenera `his-backend` (konflikt portu 10420).
Porty hosta można przesłonić zmiennymi `HIS_DB_HOST_PORT`, `HIS_SNOWSTORM_HOST_PORT`,
`<PROJECT>_HOST_PORT`; jeśli porty 5432/8080 są zajęte, ustaw `HIS_DB_HOST_PORT` /
`HIS_SNOWSTORM_HOST_PORT`. Wolumeny stosu: `hospital-information-system_*`.

Zmienne do ręcznego dopisania w zdalnym `config.env` (istniejący plik nie jest nadpisywany):
`HIS_JWT_SECRET` (WYMAGANE, min. 32 bajty - backend bez niego nie wystartuje; compose przekazuje
je jako `${HIS_JWT_SECRET:-}`), opcjonalnie `HIS_JWT_TTL`, `HIS_JWT_ISSUER`,
`HIS_LOCKOUT_MAX_ATTEMPTS`, `HIS_LOCKOUT_DURATION`, `HIS_CORS_ALLOWED_ORIGINS`,
`HIS_WS_ALLOWED_ORIGINS`, `HIS_LIQUIBASE_CONTEXTS`, `HIS_SNOWSTORM_*`, `COMPOSE_PROFILES=terminology`
(wzór: `.env.example`).

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

Backend używa tokenów JWT (HS256, bezstanowo): frontend loguje się przez `POST /api/v1/auth/login`, trzyma token w
pamięci i wysyła `Authorization: Bearer ...`; wylogowanie = usunięcie tokenu (token wygasa po `HIS_JWT_TTL`,
domyślnie 8h; brak serwerowej listy unieważnień, więc zablokowanie konta działa dopiero po wygaśnięciu tokenu).

Zmienne w zdalnym `config.env` (istniejący plik nie jest nadpisywany - dopisać ręcznie):

- `HIS_JWT_SECRET` - WYMAGANE, min. 32 bajty (np. `openssl rand -base64 48`). Bez niego backend nie startuje.
  Zmiana klucza unieważnia wszystkie wystawione tokeny.
- `HIS_JWT_TTL` - opcjonalnie (domyślnie `8h`).

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
