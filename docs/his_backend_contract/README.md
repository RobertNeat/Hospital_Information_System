# Kontrakt wystawiany przez his_backend

Ten katalog opisuje, **co `apps/his_backend` faktycznie wystawia** (strona dostawcy): REST `/api/v1`, STOMP `/ws`, bezpieczeństwo, bazę, konfigurację. Źródłem prawdy jest kod backendu (kontrolery, DTO, `@PreAuthorize`, `RolePermissions`, `GlobalExceptionHandler`, `SecurityConfig`, `realtime/*`, `db/changelog`), nie dokumenty frontendu.

Relacja do [`../his_frontend_contract`](../his_frontend_contract/README.md): tam są **oczekiwania** frontendu (typy TS), tu jest **faktyczna oferta**. Gdzie się rozchodzą, spis jest w sekcji [Odstępstwa od his_frontend_contract](#odstępstwa-od-his_frontend_contract).

## Stan

- Data opisu: 2026-10-01.
- Zakres: moduły `auth`, `staff`, `patient`, `ehr`, `catalog`, `lab`, `imaging`, `prescription`, `vitals`, `messaging`, `alert`, `dashboard`, `terminology`, `realtime`.
- Integracja FHIR z usługami `e-*`: gotowa dla recept (`e-receipt`), badań laboratoryjnych (`e-laboratory`) i obrazowych (`e-imaging`), [rest-api-fhir.md](rest-api-fhir.md). Transport FHIR: mTLS, domyślnie włączony (patrz [deployment-and-config.md](deployment-and-config.md)).

## Indeks

| Plik | Zawartość |
| --- | --- |
| [auth-and-security.md](auth-and-security.md) | JWT, logowanie/rejestracja/blokada kont, konta demo, macierz ról i uprawnień, reguły dostępu, 401/403, CORS/CSRF |
| [conventions.md](conventions.md) | ID, daty, enumy, null vs brak pola, paginacja i sortowanie, `ProblemDetail` i kody błędów, `version`, wyszukiwanie, aktor z tokenu |
| [rest-api.md](rest-api.md) | indeks endpointów i wspólne reguły; szczegóły w podplikach `rest-api-*.md` |
| [rest-api-auth-staff.md](rest-api-auth-staff.md) | `/auth/*`, `/staff`, `/wards`, `/dashboard/stats` |
| [rest-api-patient.md](rest-api-patient.md) | `/patients`, przyjęcia, wypis |
| [rest-api-ehr.md](rest-api-ehr.md) | EHR pacjenta, słownik ICD-10 |
| [rest-api-lab.md](rest-api-lab.md) | katalog, zlecenia i wyniki laboratoryjne |
| [rest-api-imaging.md](rest-api-imaging.md) | katalog, sloty, zlecenia i wyniki obrazowe |
| [rest-api-prescription.md](rest-api-prescription.md) | leki, recepty, kontrola bezpieczeństwa leku |
| [rest-api-vitals.md](rest-api-vitals.md) | parametry życiowe, anomalie, progi, przegląd oddziału |
| [rest-api-messaging.md](rest-api-messaging.md) | wątki/wiadomości, zadania, przekazania zmiany, alerty |
| [data-types.md](data-types.md) | DTO żądań i odpowiedzi, enumy (wartości na drucie), mapowanie na typy TS |
| [realtime-stomp.md](realtime-stomp.md) | endpoint `/ws`, uwierzytelnianie, tematy i payloady |
| [terminology-snomed.md](terminology-snomed.md) | `/terminology/snomed/*`, Snowstorm Lite |
| [rest-api-fhir.md](rest-api-fhir.md) | FHIR R4: `/fhir/MedicationRequest/{id}`, `/fhir/ServiceRequest/{id}`, `/fhir/DiagnosticReport` (mTLS), klienci e-receipt, e-laboratory i e-imaging, `eRxKey`, zmiana stanu recepty/zlecenia lab i obrazowego, wynik lab i obrazowy |
| [database-and-data.md](database-and-data.md) | schemat, Liquibase i konteksty, mocki |
| [deployment-and-config.md](deployment-and-config.md) | compose, porty, zmienne `HIS_*`, uruchamianie lokalne |
| [events.md](events.md) | zdarzenia domenowe, konsumenci, zdarzenie -> alert |

## Baza URL

| Co | Wartość |
| --- | --- |
| REST | `/api/v1` (wszystkie kontrolery mają prefiks `/api/v1/...`) |
| STOMP (WebSocket) | `/ws` (poza `/api/v1`) |
| Port backendu | `10420` (HTTP: `/api`, `/ws`; host i kontener 10420); `10424` (HTTPS z mTLS, wyłącznie `/fhir/**`); `10440` (zarządzanie, health; nie publikowany). Bez mTLS (`HIS_MTLS_ENABLED=false`) wszystko na `10420` |
| Health | `/actuator/health/**` (publiczne; poza `/api/v1`) |
| FHIR (usługi `e-*`) | `/fhir/**` (poza `/api/v1`; port 10424, mTLS: certyfikat klienta z dozwolonym CN) |
| Proxy w produkcji/compose | nginx we `his-frontend` (port 10400): `/api/` i `/ws` -> `http://his-backend:10420` |
| Proxy w trybie dev (Angular) | `apps/his_frontend/proxy.conf.json`: `/api` i `/ws` (`ws: true`) -> `http://localhost:10420` |

Frontend i backend są za jedną origin, dlatego CORS jest domyślnie wyłączony (patrz [auth-and-security.md](auth-and-security.md#cors-i-csrf)).

## Zasady utrzymania

1. Zmiana kontrolera, DTO, enuma, `@PreAuthorize`, `RolePermissions`, mapowania błędów, tematów STOMP, changesetów lub zmiennych `HIS_*` wymaga aktualizacji tych dokumentów **w tym samym PR**.
2. Opisujemy tylko to, co jest w kodzie. Nie przepisujemy propozycji z `his_frontend_contract`.
3. Gdy backend świadomie odstępuje od oczekiwań frontendu, odstępstwo trafia do sekcji poniżej (a nie do `his_frontend_contract`).

## Odstępstwa od his_frontend_contract

Różnice między oczekiwaniami frontendu (`docs/his_frontend_contract/API.md`, `CONVENTIONS.md`) a kodem backendu.

### Ścieżki i parametry

| Temat | `his_frontend_contract` | Backend (kod) |
| --- | --- | --- |
| Nazwy/ścieżki endpointów | wg API.md | Ścieżki zgodne z API.md; backend dodaje elementy wymienione niżej |
| `GET /message-threads/{threadId}` | brak wiersza | jest (`message:read`, tylko uczestnik) |
| `GET /auth/register/wards` | brak | jest, publiczny (lista oddziałów dla rejestracji konta; `GET /wards` wymaga `ward:read`) |
| `GET /patients/{id}/clinical-notes/{noteId}`, `/diagnoses/{id}`, `/allergies/{id}` | brak | są (cele nagłówka `Location`; `ehr:read` / `ehr:read-limited`) |
| `POST /patients/{id}/admissions` (`outpatient`) | `wardId`, `attendingPhysicianId`, `reason` wymagane | opcjonalne dla `outpatient` (`Admission.wardId`/`attendingPhysicianId` i `Encounter.practitionerId` mogą być nieobecne) |
| 409 duplikat PESEL | sam status | + `errors[{ field: "pesel", code: "duplicate" }]` |
| `GET/POST /patients/{id}/diagnoses`, `/allergies` | "propozycja" | zaimplementowane |
| `GET /vital-thresholds`, `/auth/*`, `POST /staff/{id}/activate`, `/lock` | "propozycja" | zaimplementowane |
| `GET /terminology/snomed/*` | brak | zaimplementowane ([terminology-snomed.md](terminology-snomed.md)), w tym `GET /terminology/snomed/suggestions` (podpowiedzi wg specjalizacji zalogowanego lekarza) |
| `GET /staff` | `role?` (`wardId?` w typie) | `role`, `wardId` oba działają |
| `GET /dictionaries/icd-10` | brak parametrów | `term` i `size` (domyślnie 50, maks. 100; `size` < 1 -> 422) |
| `GET /lab-orders` | `patientId`, `status`, `urgency` | + `orderedFrom`, `orderedTo` (ISO-8601, włącznie, po `orderedAt`) |
| `GET /prescriptions` | `patientId`, `prescriberId` | + `status` (efektywny), `kind` |
| `GET /handoff-notes` | `wardId` | + `shiftDate` (data) |
| `POST /drug-safety-checks` | `patientId`, `drugId`, `dosage?` | + `items[]` (cała robocza recepta; kontrola duplikatów i interakcji między pozycjami) |

### Błędy i walidacja

| Temat | `his_frontend_contract` | Backend (kod) |
| --- | --- | --- |
| Nieznany kod/slot w ciele zlecenia | API.md §4/§5: 404 (pacjent / `testCode` / `examCode`) | **422** `VALIDATION_FAILED` z `errors[].code="notFound"` (`items[i].testCode`, `examCode`, `slotId`). 404 tylko dla pacjenta ze ścieżki |
| Nieistniejące odwołania w ciele innych żądań | 404 lub 422 (różnie) | 422: `wardId`/`attendingPhysicianId` przy przyjęciu, `wardId` przy rejestracji, `encounterId`, `toId`/`patientId` w przekazaniu zmiany, `summaryNoteId`. 404: `assignedToId`/`patientId` zadania, uczestnicy/pacjent wątku |
| `acknowledge` wyniku | 409 przy niezgodnym `version` | `version` **ignorowane**; operacja idempotentna, nigdy 409 z tego powodu |
| Terminologia | 422 dla błędów wejścia | **400** (bez `code`) dla błędnych parametrów, **503** (bez `code`) gdy serwer niedostępny/wyłączony, 404 z `code=NOT_FOUND` |
| 405/415 | brak ustaleń | `ProblemDetail` bez `code` (kontrakt nie przewiduje kodu) |
| Niepoprawny UUID w ścieżce | brak ustaleń | **404** (ID są nieprzezroczyste); niepoprawny UUID/enum w query lub w ciele -> 422 (`typeMismatch`/`invalidFormat`) |
| Reguła `fasting` zlecenia lab | brak | 422 `fastingRequired`, gdy badanie wymaga czczości, a `fasting` != true |

### Uprawnienia

| Temat | `his_frontend_contract` | Backend (kod) |
| --- | --- | --- |
| `drug-safety-checks` | doctor "W/R", nurse/pharmacist/admin "R" | `POST /drug-safety-checks` wymaga `drug-safety-check:run` = **tylko `doctor`**; `drug:read` (katalog) mają doctor, nurse, pharmacist, admin |
| Status zadania | "reguły do potwierdzenia" | Zmienić status może wyłącznie osoba przypisana lub twórca (403 inaczej). `admin` ma tylko `task:read` (bez `task:write`), więc nie zmienia statusu. Przejście `open -> done` jest dozwolone |
| `ehr:read-limited` (laborant, radiolog, farmaceuta) | laborant/radiolog "R (ograniczone)", farmaceuta "R (alergie/leki)" | Dla wszystkich trzech ról dokładnie: `GET diagnoses`, `allergies`, `contraindications`, `treatments`. Brak: `ehr-summary`, `encounters`, `episodes`, `clinical-notes` |
| Recepty: realizacja i `eRxKey` | `eRxKey` "z P1/e-recepty" (ERD), realizacja bez endpointu | `partially_dispensed`/`dispensed`/`expired`/`cancelled` przychodzą z e-receipt przez `PUT /fhir/MedicationRequest/{id}` (mTLS, [rest-api-fhir.md](rest-api-fhir.md)); `eRxKey` jest lokalny, a dla `e_prescription` podmieniany kluczem z e-receipt po commicie (odpowiedź `201` wystawienia ma jeszcze klucz lokalny, `version` bez zmian). Integracja domyślnie wyłączona, bez e-receipt zostaje klucz lokalny |
| Zlecenia i wyniki lab a e-laboratory | zmiana stanu zlecenia i wynik z UI laboratorium (kontrakt: tylko REST `/status`, `/cancel`; wynik bez endpointu) | `PUT /fhir/ServiceRequest/{id}` zmienia stan wg `LabOrderStateMachine` (aktor `null`, bez wymogu powodu anulowania); `POST /fhir/DiagnosticReport` zapisuje wynik przez `recordResult` (aktor `null`, wykonawca z `performer.display`), auto-`completed` jak dotąd ([rest-api-fhir.md](rest-api-fhir.md#badania-laboratoryjne-e-laboratory)). Do e-laboratory trafiają tylko nowe zlecenia i anulowanie z HIS; integracja domyślnie wyłączona |
| Zlecenia i wyniki obrazowe a e-imaging | zmiana stanu zlecenia i wynik z UI pracowni (kontrakt: tylko REST `/status`, `/cancel`; wynik bez endpointu) | `PUT /fhir/ServiceRequest/{id}` zmienia stan wg `ImagingOrderStateMachine` (aktor `null`, bez wymogu powodu anulowania, anulowanie zwalnia slot); `POST /fhir/DiagnosticReport` zapisuje wynik przez `recordResult` (aktor `null`, radiolog z `performer.display`), auto-`completed` jak dotąd ([rest-api-fhir.md](rest-api-fhir.md#badania-obrazowe-e-imaging)). Ścieżki `/fhir/ServiceRequest` i `/fhir/DiagnosticReport` są wspólne z laboratorium (routing po id zlecenia / systemie kodu badania). Do e-imaging trafiają tylko nowe zlecenia i anulowanie z HIS; integracja domyślnie wyłączona |
| Zapis uprawnień bez endpointu | admin "W" dla `vital-thresholds`, `staff`, `wards`; laborant/radiolog "W" wyników | `vital-threshold:write`, `staff:write`, `ward:write`, `lab-result:write`, `imaging-result:write` są w macierzy i tokenie, ale **żaden kontroler ich nie sprawdza** (brak endpointów zapisu). Administrator ma tylko `activate`/`lock` (`account:manage`) |
| Radiolog a notatki | radiolog "W (consultation)" | Radiolog może `POST /clinical-notes` (kategoria `consultation`), ale nie może ich odczytać (`GET /clinical-notes` wymaga `ehr:read`) |
| Wątki admina | "W (tylko własne wątki)" | Każda rola ma `message:read/write`; dostęp do wątku mają wyłącznie uczestnicy (nie-uczestnik -> 403, także admin) |

### Wyniki lab/obrazowe

| Temat | `his_frontend_contract` | Backend (kod) |
| --- | --- | --- |
| Wprowadzanie wyniku | "poza kontraktem UI" | **Nie ma endpointu REST** `POST` wyniku lab/obrazowego. Zapis tylko przez serwisy `LabResultRecordingService` i `ImagingResultRecordingService`, wywoływane z `POST /fhir/DiagnosticReport` (usługi `e-laboratory`/`e-imaging`, mTLS); w REST wyniki pochodzą z tego zapisu albo z danych mock |
| Auto-`completed` zlecenia | propozycja | Wdrożone przy zapisie wyniku (reguły w [rest-api-lab.md](rest-api-lab.md) i [rest-api-imaging.md](rest-api-imaging.md)) |
| Filtr `abnormal` wyników obrazowych | `critical` = `critical:true` | `abnormal` i `critical` zawężają do `critical = true` (identycznie) |
| Zlecenie obrazowe ze `slotId` | brak | Zlecenie od razu `scheduled` (historia: `ordered`, `scheduled`), slot `available=false`; anulowanie zwalnia slot |

### Kształt odpowiedzi

| Temat | Opis |
| --- | --- |
| `ClinicalAlert.link` | Pole nie jest zwracane (backend nie zna tras UI); jest `target` |
| Kolejność elementów | Wartości typu zbiór są zwracane w kolejności deklaracji enuma lub alfabetycznie, nie w kolejności wejścia: `Patient.flags` (kolejność enuma), `Drug.routes`/`reimbursementOptions`, `LabTest.specimenTypes`, `DosageInstruction.timesOfDay` (kolejność enuma), `ClinicalNote.symptoms`, `Allergy.atcCodes`, `Drug.interactsWithAtc`, `LabPanel.testCodes` (alfabetycznie). Pozycje recepty są sortowane po `drugName` (potem `id`), obserwacje wyniku lab po `analyteCode`, `HandoffNote.patientNotes` po `patientId` |
| Puste kolekcje | `symptoms` i `atcCodes` są pomijane, gdy puste; `Drug.interactsWithAtc` pomijane gdy puste. Pozostałe listy zwracane jako `[]` |
| `pesel` | Zawsze obecne w `Patient`/`PatientSummary` (`null` = brak numeru); jedyne pole z `@JsonInclude(ALWAYS)` |
| `ProblemDetail.type` | `urn:his:problem:<kod-kebab>` (np. `urn:his:problem:validation-failed`), a nie `about:blank`, gdy jest `code` |
| `StaffRegistrationRequest` | TS wymaga `specialization` i `pwz`; backend przyjmuje je jako opcjonalne (`pwz` puste lub 7 cyfr) |
| Presence | `StaffMember.online` jest projekcją sesji STOMP w pamięci jednej instancji |

### STOMP

| Temat | `his_frontend_contract` | Backend (kod) |
| --- | --- | --- |
| Transport i uwierzytelnienie | `/ws` (SockJS opcjonalnie); sesja cookie albo `Authorization` w CONNECT | Czysty WebSocket (bez SockJS); wyłącznie nagłówek `Authorization: Bearer` w CONNECT, brak cookie. Ważność tokenu sprawdzana tylko przy CONNECT |
| Wysyłanie | "bez `/app/**`" | Każde `SEND` jest odrzucane |
| `/topic/alerts/{wardId}` | personel z `wardId`, `admin` wszystkie | Wymaga `alert:read` i własnego oddziału (albo roli `admin`) |

### Ograniczenia opisu

- `WardVitalsRow`: kształt zgodny z `vitals.model.ts` (`latest` i `lastMeasuredAgoMin` pomijane, gdy brak odczytów); kolejność: najcięższe najpierw; filtr: pacjenci `admitted` z aktywnym przyjęciem.
- Hasła kont mock `EMP-0001`…`EMP-0010`: wspólne hasło demo wg README generatora; hash BCrypt nie pozwala tego zweryfikować.
- Kod odpowiedzi (401 czy 403) dla ścieżek spoza `/api/**`, `/ws/**`, `/actuator/health/**` (objęte `denyAll`) nie jest zweryfikowany uruchomieniem.
