# Uruchomienie lokalne (Docker Compose)

Jeden plik `deploy/compose.yml` definiuje cały stos (postgres, his-backend, his-frontend, e-receipt,
e-laboratory, e-imaging, opcjonalnie snowstorm-lite w profilu `terminology`). Lokalnie dokłada się
nakładkę `deploy/compose.dev.yml` (build z źródeł, porty na `127.0.0.1`) i `deploy/local.env`.

`deploy/local.env` nie jest w repo (jest w `.gitignore`) - przy pierwszym uruchomieniu utwórz go z szablonu:

```powershell
Copy-Item deploy/local.env.example deploy/local.env
```

Pełny stos (z poziomu root katalogu projektu; `pnpm stack:up` / `pnpm stack:down`):

```powershell
docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env up -d --build
docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env down
```

Frontend: http://localhost:10400, backend: http://localhost:10420, Postgres: `127.0.0.1:5432`
(baza/użytkownik/hasło `his`). Logowanie demo: `admin` / `admin`.

Tryb IDE (backend z IntelliJ/Maven, profil `dev`) - uruchamiamy tylko bazę (`pnpm stack:db`) i NIE
startujemy kontenera `his-backend` (konflikt portu 10420):

```powershell
docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env up -d postgres
pnpm start:backend
```

Wolumeny stosu: `hospital-information-system_postgres-data` (baza) i
`hospital-information-system_snowstorm-lite-data` (indeks Snowstorm; wymaga importu RF2).
Jeśli porty 5432/8080 są zajęte, ustaw `HIS_DB_HOST_PORT` / `HIS_SNOWSTORM_HOST_PORT` w `deploy/local.env`.

# Uruchomienie kontenera SNOWSTORM lite i dostarczenie danych:

1. uruchomienie kontenera SNOWSTORM lite ( z poziomu root katalogu projektu)

```powershell
PS P:\Hospital_Information_System> docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env --profile terminology up -d
```

2. załadowanie danych (z poziomu katalogu zawierającego plik '')

```powershell
PS D:\SNOMED_CT_international_release(licensed-non-commercial)> ls


    Directory: D:\SNOMED_CT_international_release(licensed-non-commercial)


Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
-a----        30.09.2026     18:15         363728 doc_SnomedCTReleaseNotes_Current-en-US_INT_20261001.pdf
-a----        30.09.2026     18:23      587754816 SnomedCT_InternationalRF2_PRODUCTION_20261001T120000Z.zip


PS D:\SNOMED_CT_international_release(licensed-non-commercial)> C:\Windows\System32\curl.exe -u admin:admin `
>>   --form file=@SnomedCT_InternationalRF2_PRODUCTION_20261001T120000Z.zip `
>>   --form version-uri="http://snomed.info/sct/900000000000207008/version/20261001" `
>>   http://localhost:8080/fhir-admin/load-package


```

3. weryfikacja importu

http://localhost:8080/fhir/?tx=http%3A%2F%2Flocalhost%3A8080%2Ffhir#syndication

w 'Installed SNOMED CT Editions' powinna pojawić się wersja '20261001'

Po tych krokach uruchom aplikację z `HIS_SNOWSTORM_ENABLED=true` (w `deploy/local.env`)

4. zatrzymanie :

```powershell
PS P:\Hospital_Information_System> docker compose -f deploy/compose.yml -f deploy/compose.dev.yml --env-file deploy/local.env --profile terminology down
[+] down 2/2
 ✔ Container his-dev-postgres-1 Removed                                                                                                                                                             0.6s
 ! Network his-dev_default      Resource is still in use                                                                                                                                            0.0s
PS P:\Hospital_Information_System>
```
