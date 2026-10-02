# export-mocks

Generator danych referencyjnych i demonstracyjnych Liquibase z mocków TypeScript frontendu
(`src/app/mock-data/*.mock.ts`, `mock-utils.ts`, `constants/vitals-thresholds.ts`).
Mocki pozostają jedynym źródłem prawdy; pliki SQL są **generowane i zacommitowane** (nie edytuj ich ręcznie).

## Uruchomienie

```
pnpm --filter his-frontend export:mocks      # albo: cd apps/his_frontend && pnpm export:mocks
```

Skrypt to zwykły `vitest run` z osobnym configiem (`vitest.export.config.mts`, pliki `*.export.ts`);
nie wchodzi do `pnpm test`. Nie wymaga dodatkowych bibliotek.

Wyjście:

| Plik                                                                   | Zawartość                                                        |
| ---------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `apps/his_backend/src/main/resources/db/changelog/reference/NNN-*.sql` | progi parametrów życiowych (`context:reference`) |
| `apps/his_backend/src/main/resources/db/changelog/mock/NNN-*.sql`      | dane demonstracyjne (`context:mock`)                             |
| `apps/his_backend/src/test/resources/db/mock-manifest.json`            | liczności wierszy per tabela (używane przez testy backendu)      |

Numeracja plików odpowiada kolejności kluczy obcych (Liquibase `includeAll` ładuje alfabetycznie):
`001-staff`, `002-catalogs`, `003-schedule-slots`, `004-patients`, `005-ehr`, `006-lab`, `007-imaging`,
`008-prescriptions`, `009-vitals`, `010-messaging`.

Kontekst `mock` jest ładowany tylko gdy `spring.liquibase.contexts` zawiera `mock` (profil `dev`:
`reference,mock`; domyślnie tylko `reference`). Changeset mock bez kontekstu jest niedozwolony.

## Determinizm (checksumy Liquibase)

Dwa uruchomienia generatora muszą dać identyczne bajty (sprawdź np. `sha256sum` katalogów `reference/`, `mock/`).

- Zegar jest przypięty (`vi.setSystemTime`, stała `T0` w `time.ts`: 2026-06-15 12:00 Europe/Warsaw) i
  `TZ=Europe/Warsaw`; mocki ładowane są dynamicznym `import()` dopiero po ustawieniu zegara.
- Czasy nie są literałami z chwili generacji, tylko wyrażeniami względem chwili migracji:
  `now() - interval '76 hours 30 minutes'`; daty względne: `CURRENT_DATE - 3`. Różnice względem `T0` są zachowane
  dokładnie (CHECK-i kolejności nadal działają). Konsekwencja: godzina z zegara ściennej (np. „08:00”) nie jest zachowana.
  Wyjątek: sloty obrazowania są zakotwiczone w lokalnej północy dnia migracji (godziny pracy 08:00-16:00).
- Identyfikatory: UUIDv5 (SHA-1, `node:crypto`) ze stałej przestrzeni nazw `MOCK_NAMESPACE` (`uuid.ts`; **nigdy nie zmieniaj**)
  z nazwy = id z mocka (`uuidv5('pat-001')`). Rekordy bez własnego id dostają nazwę strukturalną, np.
  `lab-order-item/lord-001/0`, `user-account/stf-001`, `prescription-item/rx-001/0`.
- Kolejność wierszy = kolejność w mockach; escapowanie apostrofów w literałach; końce linii LF.

## Decyzje mapowania

- Katalogi (`lab_test`, `lab_analyte_definition`, `lab_panel`, `imaging_exam`, `drug`, `schedule_slot`) to **mock**, nie reference.
  Reference: `vital_threshold` (6). HIS przechowuje wyłącznie SCTID (SNOMED CT); nie ma lokalnego słownika terminologii
  (`icd10_code` usunięty - zob. `cleanup/001-drop-icd10-code.sql`), treść serwuje Snowstorm Lite.
- Brak konta administratora bootstrap (decyzja otwarta).
- Konta: każdy `StaffMember` z `employeeId` dostaje `user_account` (login = `employeeId`, np. `EMP-0001`),
  wspólne hasło demo: **`HisDemo2026!`** (BCrypt cost 10, stała `DEMO_PASSWORD_HASH` w `build.ts`; wygenerowana jednorazowo
  przez `BCryptPasswordEncoder`, sól jest częścią hasha, więc stała jest deterministyczna).
- `Patient.currentAdmission` -> wiersz `admission` (`active`; dla `dischargedAt` -> `discharged`, disposition `home`).
  `encounter_id` wskazuje istniejący kontakt (hospitalizacja/SOR na tym samym oddziale); syntetyczny kontakt powstaje tylko, gdy żaden nie pasuje.
  `discharge_summary_note_id` ustawiane `UPDATE`-em po notatkach (notatka `discharge` pacjenta).
- MRN z mocków są jawne; changeset ustawia `setval('patient_mrn_seq', <max>)`.
- `MessageThread`: `thread_participant.last_read_at` = `sentAt` ostatniej wiadomości z `readByIds` uczestnika
  (generator sprawdza, że wynikowy `unreadCount` zgadza się z mockiem). `acknowledged` -> `alert_acknowledgement`.
- Pominięte projekcje: `StaffMember.online`, `ClinicalAlert.link`, `currentAdmission` jako obiekt, `participantIds`, `readByIds`.
- Sloty obrazowania (`generateSlots`): okno -7…+14 dni względem `T0` (3168 wierszy, jeden `INSERT ... SELECT ... VALUES`).
- `lab_test` i `lab_test_specimen` w jednym changesecie (odroczony FK `default_specimen`).

## Polityka zmian

- Zmiana mocków = regeneracja (`pnpm export:mocks`) i commit wygenerowanych plików.
- Changesety, które trafiły już na jakąkolwiek bazę, mają zapisany checksum: ich zmiana przerywa start (`Validation Failed`).
  Zmiany danych mock wdrażaj jako **nowe** changesety (nowy plik `mock/NNN-*.sql`) albo `runOnChange:true`
  wyłącznie w kontekście `mock`, z `DELETE` przed `INSERT`. Na bazach deweloperskich dopuszczalne jest też odtworzenie bazy.
- **Nigdy** nie edytuj changesetów `schema/` (zmiany schematu tylko nowymi changesetami).
