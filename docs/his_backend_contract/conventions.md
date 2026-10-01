# Konwencje API

Konwencje wspólne dla wszystkich endpointów `his_backend`: identyfikatory, daty, enumy, null, paginacja, błędy, wersjonowanie, wyszukiwanie, aktor. Powrót: [README.md](README.md).

Źródła w kodzie: `application.properties` (Jackson, paginacja), `common/api/*`, `common/web/GlobalExceptionHandler`, `common/wire/*`, `common/text/TextFolding`, `common/persistence/*`.

## Identyfikatory

- Wszystkie ID zasobów to **UUID** (na drucie string). Wyjątki - klucze naturalne: `LabTest.code`, `ImagingExam.code`, `LabAnalyteDefinition.code`, `VitalThreshold.type`, `Coding.code`.
- `Patient.mrn` nadaje backend: `HIS/<rok>/<6 cyfr>` (sekwencja `patient_mrn_seq`).
- Identyfikator w ścieżce jest nieprzezroczysty: niepoprawny format UUID to po prostu "nie istnieje" -> **404** `NOT_FOUND` (nie 422). Wyjątek: UUID w query lub w ciele o złym formacie -> 422.
- Nowe zasoby tworzone przez `POST` zwracają 201 i nagłówek `Location` tam, gdzie kontroler go ustawia (patrz [rest-api.md](rest-api.md)).

## Daty i czas

| Typ | Format na drucie | Java |
| --- | --- | --- |
| Moment | ISO-8601 UTC z `Z` (np. `2026-01-31T10:15:00Z`; `write-dates-as-timestamps=false`, `time-zone=UTC`) | `Instant` |
| Data | `YYYY-MM-DD` | `LocalDate` |

- Parametry query `Instant` (`orderedFrom`, `orderedTo`) przyjmują ISO-8601; `LocalDate` (`shiftDate`, `date`) - `YYYY-MM-DD`.
- `GET /imaging-slots?date=`: doba kalendarzowa w strefie `Europe/Warsaw` (`ImagingCatalogService.CLINIC_ZONE`), zwracane `start`/`end` w UTC.
- Wygasanie recept: porównanie `validUntil` z "dziś" w UTC.
- Czas systemu = `Instant.now()` na serwerze; klient nie ustawia `createdAt`/`updatedAt`.

## Enumy

Wartość na drucie (JSON, parametry query, kolumny `varchar` + `CHECK`) to wartość z interfejsu `WireEnum.wire()`, a nie nazwa stałej Javy (np. `PERCENT_100` <-> `"100%"`, `BLOOD_0_PLUS` <-> `"0+"`, `ICD_10` <-> `"ICD-10"`). Nigdy `ORDINAL`. Pełna lista wartości: [data-types.md](data-types.md#enumy).

Błędna wartość enuma:

- w ciele JSON -> 422 `VALIDATION_FAILED`, `errors[]`: `field` = ścieżka (np. `items[0].specimenType`), `code="invalidFormat"`, `message="Niepoprawna wartosc '<x>'; dozwolone: a, b, c"`;
- w query lub ścieżce -> 422, `code="typeMismatch"`, ten sam format komunikatu.

## Null a brak pola

`spring.jackson.default-property-inclusion=non_absent`: pola `null` są **pomijane** w odpowiedziach (nie `null`). Puste kolekcje nie są pomijane (zwracane `[]`), poza polami, które mapper celowo zamienia na `null` gdy puste (`ClinicalNote.symptoms`, `Allergy.atcCodes`, `Drug.interactsWithAtc`).

Pola z semantycznym `null` (zawsze obecne): `Patient.pesel` i `PatientSummary.pesel` (`@JsonInclude(ALWAYS)`).

`LabObservation.referenceRange` jest zawsze obecne (pusty obiekt `{}`, gdy brak zakresu).

`PATCH /patients/{id}`: brak pola = bez zmian, `null` = wyczyszczenie, obiekty zagnieżdżone (`address`, `insurance`, ...) zastępowane w całości. Pola spoza listy "patchowalnych" są ignorowane.

Nieznane pola w ciałach żądań nie powodują błędu (m.in. `id`, `status`, `orderedById`, `testName`).

## Paginacja

Tylko listy oznaczone jako stronicowane w [rest-api.md](rest-api.md) (`PageResponse<T>`):

| Parametr | Znaczenie |
| --- | --- |
| `page` | numer strony od 0 |
| `size` | rozmiar strony; domyślnie **20**, maksymalnie **100** (większe jest obcinane do 100) |
| `sort` | `pole,asc` lub `pole,desc` (może wystąpić wielokrotnie) |

Odpowiedź:

```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
```

Sortowanie jest ograniczone **białą listą pól per endpoint**; nieznane pole -> 422 `VALIDATION_FAILED`, `errors[0].field="sort"`, komunikat z listą dozwolonych pól. Brak `sort` = sort domyślny. Remisy rozstrzyga `id` rosnąco (stabilne strony).

| Endpoint | Dozwolone `sort` | Domyślnie |
| --- | --- | --- |
| `GET /patients` | `lastName`, `firstName`, `birthDate`, `mrn`, `status`, `createdAt` | `lastName`, `firstName` |
| `GET /lab-orders` | `orderedAt`, `plannedCollectionAt`, `status`, `urgency`, `createdAt` | `orderedAt` desc |
| `GET /lab-results` (inbox) | `resultedAt`, `collectedAt`, `testCode`, `testName`, `category`, `status` | `resultedAt` desc |
| `GET /imaging-orders` | `orderedAt`, `scheduledAt`, `status`, `urgency`, `modality`, `createdAt` | `orderedAt` desc |
| `GET /imaging-results` (inbox) | `reportedAt`, `performedAt`, `modality`, `examName`, `status`, `critical` | `reportedAt` desc |
| `GET /prescriptions` | `issuedAt`, `validFrom`, `validUntil`, `status`, `kind`, `createdAt` | `issuedAt` desc |
| `GET /message-threads` | `lastMessageAt`, `subject`, `createdAt` | `lastMessageAt` desc |

Pozostałe listy (`/staff`, `/wards`, `/tasks`, `/alerts`, `/handoff-notes`, listy EHR, wyniki pacjenta, `/drugs`, katalogi, `/dictionaries/icd-10`) **nie są stronicowane** - zwracają tablicę, z własnym stałym sortowaniem opisanym w [rest-api.md](rest-api.md). Limity: `/drugs` maks. 50, `/dictionaries/icd-10` domyślnie 50 / maks. 100.

## Błędy (ProblemDetail)

Błędy mają `Content-Type: application/problem+json` (RFC 9457). Pola:

| Pole | Opis |
| --- | --- |
| `type` | `urn:his:problem:<kod-kebab>` (np. `urn:his:problem:not-found`) gdy jest `code`; inaczej domyślne Springa (`about:blank`) |
| `title` | zdanie statusu HTTP (np. `Not Found`); w terminologii polski tytuł |
| `status` | kod HTTP |
| `detail` | komunikat po polsku |
| `instance` | ścieżka żądania |
| `code` | `ApiErrorCode`: `NOT_FOUND`, `VALIDATION_FAILED`, `CONFLICT`, `FORBIDDEN`, `UNAUTHENTICATED`, `INTERNAL` (brak dla 405/415 i dla błędów terminologii poza 404) |
| `errors[]` | 422; 409 duplikatu PESEL (jedno pole): `{ field, message, code? }` (`code` - nazwa reguły: np. `NotBlank`, `required`, `notFound`, `mismatch`, `duplicate`, `range`, `typeMismatch`, `invalidFormat`, `mismatchedInput`) |

| Status | `code` | Kiedy |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` | brak/zły/wygasły token, złe dane logowania |
| 403 | `FORBIDDEN` | brak uprawnienia, reguła domenowa (np. nie-uczestnik wątku), konto `pending`/`locked`/tymczasowo zablokowane |
| 404 | `NOT_FOUND` | zasób nie istnieje, niepoprawny UUID w ścieżce, nieznana ścieżka (`NoResourceFoundException`) |
| 409 | `CONFLICT` | niezgodny `version`; niedozwolone przejście stanu; naruszenie unikalności (SQLSTATE `23505`: "Rekord o podanych danych juz istnieje"); zajęty slot; drugie aktywne przyjęcie; duplikat PESEL/`employeeId`/`pwz`/`email` (409 duplikatu PESEL niesie `errors[]` z `field="pesel"`, `code="duplicate"`) |
| 422 | `VALIDATION_FAILED` | błąd Bean Validation (`@Valid`), reguły domenowe (`ValidationFailedException`), brak wymaganego parametru (`code="required"`), zły typ parametru, nieczytelne ciało JSON (`"Tresc zadania jest niepoprawna lub nieczytelna"`), niedozwolone `sort` |
| 500 | `INTERNAL` | nieobsłużony wyjątek (`"Wystapil nieoczekiwany blad"`; szczegóły tylko w logu), naruszenie bazy inne niż unikalność |
| 405 / 415 | brak | `ProblemDetail` ze Springa bez `code` |
| 400 / 503 | brak | wyłącznie `/terminology/snomed/*` ([terminology-snomed.md](terminology-snomed.md)) |

Przykład 422:

```json
{
  "type": "urn:his:problem:validation-failed",
  "title": "Unprocessable Content",
  "status": 422,
  "detail": "Walidacja nie powiodla sie",
  "instance": "/api/v1/patients",
  "code": "VALIDATION_FAILED",
  "errors": [ { "field": "noPeselReason", "message": "Powod braku numeru PESEL jest wymagany, gdy pesel jest pusty", "code": "required" } ]
}
```

Niedozwolone przejście stanu to zawsze 409 `CONFLICT` (nie ma osobnego kodu, np. `INVALID_STATE`).

`patientId` w ciele żądania (jeśli podany) musi być zgodny ze ścieżką, inaczej 422 `errors[].field="patientId"`, `code="mismatch"`; ścieżka jest autorytatywna.

## Optymistyczne blokowanie (`version`)

- Encje z `@Version` zwracają `version` (long): `Patient`, `Admission`, `ClinicalNote`, `Diagnosis`, `Allergy`, `LabOrder`, `ImagingOrder`, `Prescription`, `TeamTask`.
- Akcje przyjmują opcjonalne `version` (`PATCH /patients` w ciele; `discharge` - wersja aktywnego przyjęcia; `status`/`cancel` zleceń; `prescriptions/{id}/cancel`; `tasks/{id}/status`). Niezgodna wartość -> 409. Pominięte `version` = brak kontroli.
- `acknowledge` wyników (lab, obrazowe) przyjmuje `version`, ale go **ignoruje**; wyniki nie mają wersji, a operacja jest idempotentna.
- Równoległa modyfikacja wykryta przez JPA (`OptimisticLockingFailureException`) -> 409 "Zasob zostal zmodyfikowany przez inna osobe...".

## Wyszukiwanie tekstowe

Parametry `term` (pacjenci, leki, ICD-10) działają jako **podciąg, bez rozróżniania wielkości liter i znaków diakrytycznych** (polskie litery i popularne europejskie, tablica `TextFolding`, np. `ł`->`l`, `ó`->`o`). Fraza jest dzielona na tokeny po białych znakach; **każdy token** musi pasować do któregoś z pól:

| Endpoint | Pola dopasowania |
| --- | --- |
| `GET /patients?term=` | nazwisko, imię, MRN, PESEL |
| `GET /drugs?term=` | nazwa handlowa, substancja czynna, kod ATC |
| `GET /dictionaries/icd-10?term=` | kod, nazwa |

Znaki `%`, `_`, `\` w frazie są traktowane dosłownie (escape).

Sortowanie katalogów nazwami (`/lab-tests`, `/lab-panels`, `/imaging-exams`) używa kolacji polskiej (`pl-PL`).

## Aktor z tokenu

Aktor każdej operacji (twórca, autor, zlecający, wystawiający, nadawca, potwierdzający) pochodzi z claima `staffId` tokenu, nigdy z ciała. Pola `orderedById`, `prescriberId`, `recordedById`, `authorId`, `diagnosedById`, `createdById`, `fromId` w żądaniach są **ignorowane**. Audyt (`createdAt`, `createdById`, `updatedAt`, `updatedById`) uzupełnia Spring Data Auditing.

## Pola nadawane przez backend (ignorowane w żądaniach)

`id`, `mrn`, `status` początkowy, `statusHistory`, `currentAdmission`, audyt, `version`, `accessCode`, `eRxKey`, `orderedAt`, `issuedAt`, `cancelledAt`, `reviewed*`, `acknowledged*`, snapshoty katalogowe (`testName`, `examName`, `modality`, `bodyRegion`, `drugName`, `activeSubstance`, `strength`, `form`), `scheduledAt` zlecenia obrazowego (ze slotu).

## Projekcje zależne od widza (`@viewerScoped`)

Liczone dla zalogowanego użytkownika: `MessageThread.unreadCount`, `ClinicalAlert.acknowledged/acknowledgedById/acknowledgedAt`, `DashboardStats.criticalAlerts` i `openTasks`, `StaffMember.online` (obecność STOMP - globalna, nie zależna od widza). `Message.readByIds` jest projekcją z kursorów odczytu wszystkich uczestników (nadawca zawsze pierwszy).
