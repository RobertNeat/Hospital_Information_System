# REST: pacjenci, przyjęcia, wypis

Endpointy `PatientController` (`/api/v1/patients`). Powrót: [README.md](README.md) | indeks: [rest-api.md](rest-api.md). Typy: [data-types.md](data-types.md#pacjent-i-przyjęcie).

| Metoda | Ścieżka | Uprawnienie | Parametry / body | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/patients` | `patient:read` | query: `term`, `status` (`PatientStatus`), `wardId` (UUID), `page`, `size`, `sort` | Pag. `PatientSummaryResponse` | 200; 422 zły parametr/`sort` |
| GET | `/patients/{patientId}` | `patient:read` | - | `PatientResponse` (z `currentAdmission`) | 200; 404 |
| POST | `/patients/duplicate-check` | `patient:read` | `DuplicateCheckRequest` (`pesel`, 11 cyfr) | `PatientSummaryResponse` albo brak treści | 200 kandydat / 204 brak; 422 zły PESEL |
| POST | `/patients` | `patient:write` | `PatientCreateRequest` | `PatientResponse` + `Location: /api/v1/patients/{id}` | 201; 409 duplikat PESEL; 422 |
| PATCH | `/patients/{patientId}` | `patient:write` | surowy JSON (`ObjectNode`), pola jak `PatientCreateRequest` + opcjonalne `version` | `PatientResponse` | 200; 404; 409 (`version`, duplikat PESEL); 422 |
| GET | `/patients/{patientId}/admissions` | `admission:read` | query: `status` (`AdmissionRecordStatus`) | `AdmissionResponse[]` (od najnowszego wg `admittedAt`, potem `id`) | 200; 404 |
| POST | `/patients/{patientId}/admissions` | `admission:admit` | `AdmitPatientRequest` | `PatientResponse` + `Location: /api/v1/patients/{id}/admissions` | 201; 404; 409 aktywne przyjęcie istnieje; 422 |
| POST | `/patients/{patientId}/discharge` | `admission:discharge` | `DischargePatientRequest` | `PatientResponse` | 200; 404; 409 brak aktywnego przyjęcia / niezgodna `version`; 422 |

## Reguły domenowe

### Lista i wyszukiwanie

- `term`: tokeny dzielone białymi znakami, każdy musi pasować do nazwiska, imienia, MRN lub PESEL jako podciąg, bez wielkości liter i diakrytyków ([conventions.md](conventions.md#wyszukiwanie-tekstowe)). Brak `term` = bez filtra tekstowego.
- `wardId` zawęża do pacjentów w statusie `admitted` z **aktywnym** przyjęciem na tym oddziale. Nieistniejący `wardId` nie daje 404 (pusty wynik).
- `PatientSummaryResponse.wardName` i `bed` są wypełnione tylko dla pacjentów `admitted` (projekcja aktywnego przyjęcia).
- `sort`: `lastName`, `firstName`, `birthDate`, `mrn`, `status`, `createdAt`; domyślnie nazwisko, imię.

### Rejestracja i edycja

- PESEL: 11 cyfr albo `null`. Gdy `pesel` jest pusty, wymagany `noPeselReason` (inaczej 422, `errors[].field="noPeselReason"`, `code="required"`).
- Duplikat PESEL -> 409 "Pacjent o podanym numerze PESEL juz istnieje" (także przy `PATCH` na PESEL innego pacjenta).
- `birthDate` nie z przyszłości (`@PastOrPresent`); `address` i `insurance` wymagane; `emergencyContact` i `identityDocument` - wszystkie pola razem albo wcale.
- `mrn`, `status`, `currentAdmission`, audyt i `version` w żądaniu są ignorowane. `mrn` nadaje backend (`HIS/<rok>/<6 cyfr>`).
- `PATCH`: brak pola = bez zmian, `null` = wyczyść; obiekty zagnieżdżone zastępowane w całości. Zmiana jest scalana ze stanem bieżącym i walidowana jak rejestracja (błędy jako 422 z `errors[]`). Patchowalne pola: `pesel`, `noPeselReason`, `identityDocument`, `firstName`, `secondName`, `lastName`, `birthDate`, `gender`, `phone`, `email`, `address`, `emergencyContact`, `insurance`, `bloodType`, `flags`. Opcjonalne `version` (liczba całkowita) porównywane z wersją pacjenta (409 przy niezgodności; nie-liczba -> 422).
- `POST /patients/duplicate-check`: PESEL w ciele (nie w URL/logach). 204 oznacza, że pacjenta o takim PESEL nie ma.

### Przyjęcie (`POST .../admissions`)

- Co najwyżej jedno aktywne przyjęcie na pacjenta; drugie -> 409 "Pacjent ma juz aktywne przyjecie".
- 422 gdy `wardId` nie istnieje albo `attendingPhysicianId` nie wskazuje pracownika w roli `doctor` (`errors[].code="notFound"`).
- `admissionType=outpatient` tworzy `Encounter` typu `visit` i status pacjenta `outpatient`; pozostałe typy tworzą `Encounter` `hospitalization` i status `admitted`.
- Odpowiedź to zaktualizowany `PatientResponse` (`currentAdmission` + `status`), nie `AdmissionResponse`.
- Zdarzenie: `PatientAdmitted` ([events.md](events.md)).

### Wypis (`POST .../discharge`)

- Wymaga aktywnego przyjęcia (inaczej 409 "Pacjent nie ma aktywnego przyjecia").
- `version` (opcjonalne) odnosi się do **aktywnego przyjęcia** (`currentAdmission.version`), nie do pacjenta; niezgodność -> 409.
- 422: `dischargedAt` wcześniejsze niż `admittedAt` (`code="beforeAdmission"`), `summaryNoteId` nie istnieje dla tego pacjenta (`code="notFound"`).
- Zamyka przyjęcie i powiązany `Encounter` (status `finished`, `endAt`), ustawia status pacjenta `discharged`. Odpowiedź: `PatientResponse` bez `currentAdmission`.
- Zdarzenie: `PatientDischarged` ([events.md](events.md)).

### Maszyna stanów `Patient.status`

Zmienia ją wyłącznie backend: `registered` -> (przyjęcie) `admitted` lub `outpatient` -> (wypis) `discharged`. Nie ma endpointu zmiany statusu; ponowne przyjęcie po wypisie jest możliwe (nowe przyjęcie, gdy brak aktywnego).
