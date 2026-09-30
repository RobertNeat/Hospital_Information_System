# Konwencje kontraktu frontend-backend HIS

Modele frontendu (`apps/his_frontend/src/app/models`) są kontraktem dla backendu (Java / Spring). Zmiany są ewolucyjne: nie zmieniamy nazw istniejących pól ani wartości unii, nowe pola dodajemy wyłącznie jako opcjonalne. Prefiks API: `/api/v1`.

## Układ plików

- `models/*.model.ts` – kształt odpowiedzi API (encje, projekcje).
- `models/api/` – typy żądań i wspólne typy transportowe: Request, Query, `Page`, `ProblemDetail`.
- `models/index.ts` eksportuje oba poziomy.

## Nazewnictwo

| Typ | Znaczenie |
| --- | --- |
| `Xxx` | pełna odpowiedź |
| `XxxSummary` | odchudzona projekcja na listy |
| `XxxCreateRequest` | ciało POST |
| `XxxUpdateRequest` | ciało PUT/PATCH |
| `XxxQuery` | parametry zapytania (filtry) |
| `Xxx<Akcja>Request` | akcja domenowa, np. `PrescriptionCancelRequest` |

Istniejące typy `*Draft` zostają jako aliasy oznaczone `@deprecated`.

## Identyfikatory

`ID` to nieprzezroczysty string (w bazie UUID, w Javie `UUID`). Klient go nie parsuje ani nie zakłada formatu (mocki używają np. `pat-001`).

## Daty

| TypeScript | Format na drucie | Java |
| --- | --- | --- |
| `ISODate` | `YYYY-MM-DD` | `LocalDate` |
| `ISODateTime` | UTC z `Z`, np. `2026-01-31T10:15:00Z` | `Instant` |

## Enumy (tabela mapowania)

W TypeScript są to stringowe unie. W Javie odpowiada im `enum` z `@JsonValue`. Wartości na drucie NIE są zmieniane, także gdy nie są poprawnymi identyfikatorami Javy:

- `ReimbursementLevel`: `'100%'`, `'50%'`, `'30%'`,
- `BloodType`: `'A+'`, `'A-'`, `'B+'`, `'B-'`, `'AB+'`, `'AB-'`, `'0+'`, `'0-'`,
- `Coding.system` (`CodingSystem`): `'ICD-10'`, `'ICD-9-PL'` (oraz `'LOINC'`, `'ATC'`, `'local'`),
- modalności obrazowania: `'USG'`, `'RTG'` (oraz pozostałe z `imaging.model.ts`).

Backend mapuje takie wartości przez `@JsonValue` na nazwy poprawne w Javie (np. `PERCENT_100`).

## Optional vs null

- `x?: T` – pole może nie wystąpić w JSON (Java: `@JsonInclude(NON_ABSENT)`).
- `T | null` – tylko gdy `null` ma znaczenie semantyczne, np. `pesel` (brak numeru PESEL).
- W PATCH: brak pola = bez zmian, `null` = wyczyść wartość.

## Referencje

- Klucz obcy zapisujemy jako `xxxId: ID`.
- `Ref` (`{ id, display }`) jest wyłącznie opcjonalną projekcją w odpowiedziach, nigdy nie zastępuje `xxxId`.

## Tagi JSDoc

- `@snapshot` – kopia z chwili zdarzenia, zapisana w wierszu (nie aktualizuje się przy zmianie źródła).
- `@projection` – wartość liczona przez backend, nie zapisywana przez klienta.
- `@viewerScoped` – wartość liczona per zalogowany użytkownik.

## Audyt

`Auditable` (`createdAt`, `createdById`, `updatedAt`, `updatedById`) wypełnia backend na podstawie sesji. Klient nigdy tych pól nie wysyła. Aktor akcji (`byId`, `recordedById`, `orderedById` itp.) docelowo także pochodzi z sesji, a nie z żądania.

## Wersjonowanie

`Versioned.version` odpowiada JPA `@Version` (optymistyczne blokowanie). Konflikt wersji kończy się odpowiedzią 409 z `ProblemDetail` (`code: 'CONFLICT'`).

## Błędy

Błędy mają postać `ProblemDetail` (RFC 9457, zgodny ze Springowym `ProblemDetail`) z opcjonalnymi `code: ApiErrorCode` i `errors: FieldError[]` (walidacja pól). Po stronie frontendu reprezentuje je `ApiError` (`utils/mock-response.ts`, pole `problem`); `mockError(message, latencyMs, problem?)` domyślnie zwraca 404 `NOT_FOUND`.

## Listy

Backend zwraca `Page<T>` i przyjmuje `PageQuery` (`sort` w formacie `field,asc`, zgodnie ze Spring Pageable). Serwisy frontendu zostają przy `T[]`; mock zwraca `items`.

## Logika po stronie serwera

Backend jest źródłem prawdy (authoritative) dla: anomalii parametrów, `DrugSafetyWarning`, `EhrSummary`, `WardVitalsRow`. Kod frontendu realizujący te obliczenia to wyłącznie implementacja mocka i nie stanowi specyfikacji.

## Reguły mapowania enumów na Javę

| Reguła | Przykład |
| --- | --- |
| Nazwa stałej: SCREAMING_SNAKE z wartości TS | `specimen_collected` -> `SPECIMEN_COLLECTED`, `fall_risk` -> `FALL_RISK`, `heartRate` -> `HEART_RATE` |
| `@JsonValue` zwraca dokładnie wartość z TS | `PERCENT_100` <-> `'100%'`, `BLOOD_0_PLUS` <-> `'0+'`, `ICD_10` <-> `'ICD-10'`, `ICD_9_PL` <-> `'ICD-9-PL'` |
| Wartości pisane wielkimi literami na drucie | `ImagingModality` (`'USG'`, `'CT'`), `DoseFrequency` (`'QD'`, `'Q4H'`), `ResultFlag` (`'N'`, `'LL'`): stała Javy identyczna z wartością, `@JsonValue` bez zmian |
| Persystencja | `@Enumerated(EnumType.STRING)` lub konwerter na wartość `@JsonValue`; nigdy `ORDINAL`; `CHECK` w bazie |
| Dodanie wartości do unii | zgodne wstecz (nowa stała); usunięcie lub zmiana nazwy wartości - zabronione |

Dotyczy wszystkich unii ze `models/` (np. `StaffRole`, `OrderStatus`, `AdmissionStatus`, `AdmissionRecordStatus`, `AlertTargetKind`); pełne listy wartości są w [ERD.md](ERD.md) przy właściwych encjach.

## Decyzje z kroków 2-11

- **Role.** `StaffRole` obejmuje: `doctor`, `nurse`, `lab_technician`, `radiologist`, `pharmacist`, `registrar`, `admin`. UI komunikacji (adresaci, wykonawcy zadań) używa na razie tylko `doctor` i `nurse`. Macierz uprawnień: [API.md](API.md#12-autoryzacja-i-role-propozycja-do-potwierdzenia). `StaffMember.accountStatus` (`pending`/`active`/`locked`) jest opcjonalne; rejestracja kończy się `pending`.
- **Konta.** Login = `employeeId` (unikalny); `pwz` unikalny, jeśli podany. `UserAccount` jest encją tylko backendową (hasło nigdy nie jest w odpowiedzi). `CurrentUser` = `StaffMember` + `permissions` (`@viewerScoped`).
- **Admission jako encja.** `Admission` jest encją ADT (historia, co najwyżej jedno `active`), właścicielem relacji 1:1 z `Encounter` typu `hospitalization` (`encounterId`). `Patient.currentAdmission` to projekcja aktywnego przyjęcia; `Patient.status` (`AdmissionStatus`) jest pochodną stanu przyjęć (stored, utrzymywana przez backend), odrębną od `Admission.status` (`AdmissionRecordStatus`).
- **Pacjent bez PESEL.** `pesel: string | null` + `noPeselReason`, unikalność PESEL częściowa (tylko gdy niepusty); `mrn` nadaje backend.
- **Akcje domenowe zamiast edycji statusu.** Zlecenia: `POST .../status` i `POST .../cancel` (`OrderStatusUpdateRequest`, `OrderCancelRequest`); `status` i `statusHistory` nie są polami żądań tworzenia ani PATCH. Recepty: `POST .../cancel`. Wypis: `POST /patients/{id}/discharge`.
- **Potwierdzenie wyniku (`ResultReview`).** Wyniki lab i obrazowe są niezmienne poza `reviewedAt`/`reviewedById`, ustawianymi przez `POST .../acknowledge` (`ResultAcknowledgeRequest`); aktor z sesji.
- **Alerty.** `ClinicalAlert.target: AlertTarget` (`kind`, `id`, `patientId?`) zastępuje `link` (`@deprecated`, backend nie zna tras UI). Alerty tworzy wyłącznie backend (`AlertCreateRequest` jest wewnętrzny). Stan potwierdzenia jest `@viewerScoped` (tabela `alert_acknowledgement`).
- **Wiadomości.** Stan odczytu w `ThreadParticipant.lastReadAt`; `Message.readByIds` i `MessageThread.participantIds`/`unreadCount` to projekcje. `markRead` ma puste ciało (`ThreadMarkReadRequest`).
- **Parametry życiowe.** `VitalSigns` to niezmienny płaski wiersz z nullable kolumnami (korekta = nowy odczyt); `POST /patients/{id}/vitals` zwraca `VitalsRecordResponse` (`saved` + `anomalies`). Progi: konfiguracja `VitalThreshold`.
- **Snapshoty.** Nazwy z katalogów (`testName`, `analyteName`, `examName`, `modality`, `bodyRegion`, `drugName`, `activeSubstance`, `strength`, `form`, `performerName`, `radiologistName`) są `@snapshot` - zapisywane w wierszu w chwili zdarzenia, backend kopiuje je z katalogu (klient może je wysyłać, ale serwer je nadpisuje).
- **Pola nadawane przez backend** (nie wysyłane przez klienta): `id`, `mrn`, audyt, `version`, `status` początkowy, `statusHistory`, `accessCode`, `eRxKey`, `orderedAt`, `issuedAt`, `cancelledAt`, `reviewed*`, `acknowledged*`.
- **Aktor z sesji.** Pola `orderedById`, `prescriberId`, `recordedById`, `authorId`, `senderId`, `createdById`, `fromId`, `acknowledgedById`, `reviewedById`, `StatusChange.byId` pochodzą z sesji. Niektóre typy żądań (`LabOrderCreateRequest`, `ImagingOrderCreateRequest`, `PrescriptionCreateRequest`, `VitalSignsCreateRequest`, `ClinicalNoteCreateRequest`, `TaskCreateRequest`, `HandoffNoteCreateRequest`) nadal zawierają to pole, bo UI je wysyła: backend je ignoruje lub waliduje względem sesji (422 przy niezgodności - do potwierdzenia).
- **Aliasy `*Draft`** (`PatientDraft`, `LabOrderDraft`, `ImagingOrderDraft`, `PrescriptionDraft`, `VitalSignsDraft`) są `@deprecated`; nowy kod używa `*CreateRequest` z `models/api`.
- **Ścieżki.** `/api/v1`, rzeczowniki w liczbie mnogiej, `/patients/{patientId}/...` dla zasobów pacjenta, akcje jako `POST .../{akcja}`; pełna lista w [API.md](API.md).
- **Kody błędów.** Niedozwolone przejście stanu (np. anulowanie zakończonego zlecenia) = 409 `CONFLICT`, tak jak konflikt `version`; błędy pól = 422 `VALIDATION_FAILED`.
