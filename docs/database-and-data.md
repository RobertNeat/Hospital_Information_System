# Baza danych i dane

Schemat PostgreSQL, Liquibase i jego konteksty, dane referencyjne/demo/mock, generator mocków.

Kod: `apps/his_backend/src/main/resources/db/changelog/*`, `application*.properties`, `apps/his_frontend/scripts/export-mocks`.

## Połączenie i JPA

| Ustawienie | Wartość |
| --- | --- |
| Silnik | PostgreSQL 17 (`postgres:17-alpine` w compose) |
| `spring.datasource.url` / `username` / `password` | `HIS_DB_URL` / `HIS_DB_USER` / `HIS_DB_PASSWORD` - **bez wartości domyślnych** w `application.properties` (aplikacja nie wystartuje bez nich) |
| Profil `dev` | domyślne: `jdbc:postgresql://localhost:5432/his`, użytkownik `his`, hasło `his`, `HIS_LIQUIBASE_CONTEXTS=reference,mock`, deweloperski klucz JWT |
| Hibernate | `ddl-auto=validate` (schemat tworzy wyłącznie Liquibase), `open-in-view=false` |
| Typy enumów | kolumny `varchar` + `CHECK` z wartością na drucie (nigdy ordinal) |
| Czas | `Instant` (UTC) |

## Schemat (48 tabel)

Pliki `db/changelog/schema/NNN-*.sql`; **54 changesety** (bez kontekstu, uruchamiane zawsze).

| Plik | Tabele |
| --- | --- |
| `001-staff.sql` | `ward`, `staff_member`, `user_account` |
| `002-patient-admission.sql` | `patient`, `patient_flag`, `treatment_episode`, `encounter`, `admission` + sekwencja `patient_mrn_seq` |
| `003-ehr.sql` | `clinical_note`, `clinical_note_symptom`, `diagnosis`, `episode_diagnosis`, `allergy`, `allergy_atc_code`, `contraindication`, `treatment`; changeset `icd10_code` nadal tu tworzy tabelę (schemat niezmienny), którą `cleanup/001-drop-icd10-code.sql` usuwa zaraz po `reference/` (tabela zostaje pusta - generator już jej nie wypełnia, zob. niżej) |
| `004-catalogs.sql` | `lab_test`, `lab_test_specimen`, `lab_analyte_definition`, `lab_panel`, `lab_panel_test`, `imaging_exam`, `schedule_slot`, `drug`, `drug_route`, `drug_reimbursement_option`, `drug_interacts_with_atc`, `vital_threshold` |
| `005-lab.sql` | `lab_order`, `lab_order_item`, `lab_order_status_change`, `lab_result`, `lab_observation` |
| `006-imaging.sql` | `imaging_order`, `imaging_order_status_change`, `imaging_result` |
| `007-prescription.sql` | `prescription`, `prescription_item`, `prescription_item_time_of_day` |
| `008-vitals.sql` | `vital_signs` |
| `009-messaging.sql` | `message_thread`, `thread_participant`, `message`, `clinical_alert`, `alert_acknowledgement`, `team_task`, `handoff_note`, `handoff_patient_note` |
| `010-outpatient-admission-optional.sql` | zmiana kolumn `admission`/`encounter` (bez nowych tabel) |
| `011-coding-system-snomed.sql` | rozszerza `CHECK` na `code_system`/`diagnosis_code_system` (`diagnosis`, `lab_order`, `imaging_order`) o wartość `SNOMED` (bez nowych tabel) |

Cechy:

- Klucze główne UUID nadawane w aplikacji; wyjątek: tabele katalogowe z kluczem naturalnym (`lab_test.code`, `imaging_exam.code`, `vital_threshold.type`).
- Kolumny audytu (`created_at`, `created_by_id`, `updated_at`, `updated_by_id`) mają encje dziedziczące po `AuditableEntity`/`VersionedEntity` (nie: `ward`, `staff_member`, `user_account`, wiele tabel pomocniczych); `version bigint` tylko w `VersionedEntity`.
- Historie statusów zleceń (`*_status_change`) są tylko dopisywane.
- Stan potwierdzenia alertu jest per użytkownik (`alert_acknowledgement`); kursor odczytu wątku w `thread_participant.last_read_at`.
- Wyniki lab/obrazowe są niezmienne poza `reviewed_at`/`reviewed_by_id`.
- Brak tabel terminologicznych: SNOMED CT (SCTID) jest jedynym kodem przechowywanym w bazie, serwowanym przez Snowstorm Lite (ECL, `$lookup`). ICD-10 **nie jest słownikiem lokalnym** - jest liczony dynamicznie na żądanie przez `GET /api/v1/terminology/snomed/{sctid}/icd-10`, który woła `ConceptMap/$translate` na Snowstorm Lite (`url=http://snomed.info/sct?fhir_cm=447562003`, refset "SNOMED CT to ICD-10 extended map"); pusta lista oznacza brak mapowania dla danego SCTID.
- Zapis diagnoz (`diagnosis.code_system`/`code_value`) i wskazań klinicznych zleceń lab/obrazowych (`lab_order.diagnosis_code_*`, `imaging_order.diagnosis_code_*`) wymaga `system='SNOMED'` z poprawnym SCTID (`^\d{6,18}$`, walidacja w `CodingValidation`); `CHECK` na `code_system` dopuszcza też starsze wartości (`ICD-10`, `LOINC`, `ATC`, `ICD-9-PL`, `local`) dla odczytu istniejących danych.
- Zmiana schematu: **wyłącznie nowym changesetem**; changesetów `schema/` nie edytuje się (Liquibase zapisuje checksum - zmiana przerywa start `Validation Failed`).

## Liquibase: kontekst a katalog

Główny plik `db.changelog-master.yaml` załącza (`includeAll`, alfabetycznie w katalogu) w kolejności: `schema/` -> `reference/` -> `cleanup/` -> `demo/` -> `mock/`. **Istnieją dwa konteksty**, `reference` i `mock`; `demo` i `cleanup` to katalogi, nie konteksty.

| Katalog | Kontekst changesetów | Liczba changesetów | Zawartość | Ładowane domyślnie? |
| --- | --- | --- | --- | --- |
| `schema/` | brak (zawsze) | 54 | DDL | tak |
| `reference/` | `reference` | 1 | `vital_threshold` (6 progów) | tak |
| `cleanup/` | brak (zawsze) | 1 | `DROP TABLE icd10_code` (tabela nadal tworzona w `schema/003-ehr.sql`, ale od usunięcia lokalnego słownika ICD-10 generator jej już nie wypełnia - zob. niżej); musi wykonać się po `reference/` | tak |
| `demo/` | **`reference`** | 3 | oddział `DEMO` + 8 kont demo (`admin`, `user`, `doctor`, `nurse`, `lab-tech`, `radiologist`, `pharmacist`, `registrar`) | tak, także produkcja |
| `mock/` | `mock` | 48 | dane demonstracyjne (pracownicy, katalogi, sloty, pacjenci, EHR, lab, obrazowanie, recepty, parametry życiowe, wiadomości/alerty/zadania), konta `EMP-0001`…`EMP-0010` | tylko gdy kontekst zawiera `mock` |

`HIS_LIQUIBASE_CONTEXTS` (`spring.liquibase.contexts`):

| Środowisko | Wartość domyślna |
| --- | --- |
| `application.properties` (bez profilu) | `reference` (bez jawnego kontekstu uruchomiłyby się też changesety `mock`) |
| profil `dev` | `reference,mock` |
| `deploy/compose.yml` | `reference` (`${HIS_LIQUIBASE_CONTEXTS:-reference}`) |
| `.env.example` (produkcja) | `reference` |
| `deploy/local.env` (lokalnie) | `reference,mock` |

Konsekwencja: konta demo z `demo/` (publiczne hasła) trafiają na każdą bazę z kontekstem `reference`, w tym produkcyjną. Changeset `mock` bez kontekstu jest niedozwolony.

## Dane mock - generator

Pliki `reference/*.sql` i `mock/*.sql` są **generowane** (nagłówek `GENERATED by apps/his_frontend/scripts/export-mocks, do not edit`) z mocków TypeScript frontendu i zacommitowane. Mocki frontendu pozostają źródłem prawdy. Ręcznie pisane są `demo/001-demo-accounts.sql` i `cleanup/001-drop-icd10-code.sql`.

| Aspekt | Opis |
| --- | --- |
| Uruchomienie | `pnpm --filter his-frontend export:mocks` (lub `cd apps/his_frontend && pnpm export:mocks`); to `vitest run` z `vitest.export.config.mts`, poza `pnpm test` |
| Wyjście | `db/changelog/reference/NNN-*.sql`, `db/changelog/mock/NNN-*.sql`, `apps/his_backend/src/test/resources/db/mock-manifest.json` (liczności wierszy do testów backendu) |
| Numeracja `mock/` | `001-staff`, `002-catalogs`, `003-schedule-slots`, `004-patients`, `005-ehr`, `006-lab`, `007-imaging`, `008-prescriptions`, `009-vitals`, `010-messaging` (kolejność kluczy obcych) |
| Determinizm | zegar przypięty (`T0` = 2026-06-15 12:00 Europe/Warsaw); czasy jako wyrażenia względem chwili migracji (`now() - interval ...`, `CURRENT_DATE - n`); UUIDv5 z nazw mocków w stałej przestrzeni `MOCK_NAMESPACE` (**nigdy nie zmieniać**); LF |
| Katalogi | `lab_test`, `lab_analyte_definition`, `lab_panel`, `imaging_exam`, `drug`, `schedule_slot` to **mock**, nie reference; reference to wyłącznie `vital_threshold` |
| Sloty obrazowania | okno -7…+14 dni względem `T0` (3168 wierszy), kotwiczone w lokalnej północy dnia migracji (08:00-16:00) |
| Konta mock | każdy pracownik z `employeeId` ma konto (login `EMP-0001`…); wspólne hasło demo wg README generatora: `HisDemo2026!` |
| Pominięte projekcje | `StaffMember.online`, `ClinicalAlert.link`, `currentAdmission` jako obiekt, `participantIds`, `readByIds` |

### Polityka zmian danych

- Zmiana mocków = regeneracja (`pnpm export:mocks`) i commit wygenerowanych plików.
- Changesety, które trafiły na jakąkolwiek bazę, mają checksum - ich edycja przerywa start. Zmiany danych `mock` wdrażać jako **nowe** changesety (nowy plik `mock/NNN-*.sql`) albo `runOnChange:true` wyłącznie w kontekście `mock`, z `DELETE` przed `INSERT`; na bazach deweloperskich można odtworzyć bazę.
- Pliki `.sql` w `db/changelog` mają `eol=lf` (`.gitattributes`), bo checksumy Liquibase zależą od bajtów.
- Zmiany `demo/` i `schema/`: tylko nowymi changesetami.

## Zmienne związane z bazą

| Zmienna | Znaczenie | Domyślnie |
| --- | --- | --- |
| `HIS_DB_URL` | JDBC URL (compose: `jdbc:postgresql://postgres:5432/${HIS_DB_NAME:-his}`) | wymagana (dev: `jdbc:postgresql://localhost:5432/his`) |
| `HIS_DB_USER` | użytkownik | wymagana (dev: `his`) |
| `HIS_DB_PASSWORD` | hasło | wymagana (dev: `his`) |
| `HIS_DB_NAME` | nazwa bazy kontenera `postgres` (tylko compose) | wymagana w compose dla `postgres` |
| `HIS_DB_HOST_PORT` | port hosta Postgresa (tylko overlay dev, loopback) | `5432` |
| `HIS_LIQUIBASE_CONTEXTS` | konteksty Liquibase | patrz wyżej |

Pełna tabela zmiennych: [deployment-and-config.md](deployment-and-config.md).
