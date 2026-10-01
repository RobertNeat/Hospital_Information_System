# REST: EHR pacjenta i słownik ICD-10

Endpointy `EhrController` (`/api/v1/patients/{patientId}`) i `DictionaryController` (`/api/v1/dictionaries`). Powrót: [README.md](README.md) | indeks: [rest-api.md](rest-api.md). Typy: [data-types.md](data-types.md#ehr).

## Dostęp

- Odczyt pełny: `ehr:read` (doctor, nurse, admin).
- Odczyt zawężony: `ehr:read-limited` (lab_technician, radiologist, pharmacist) - tylko `diagnoses`, `allergies`, `contraindications`, `treatments`. Endpointy `ehr-summary`, `encounters`, `episodes`, `clinical-notes` wymagają `ehr:read`.
- `registrar` nie ma dostępu do EHR.
- Zapis: patrz tabela; role bez uprawnienia -> 403.

## Endpointy

| Metoda | Ścieżka | Uprawnienie | Body | Odpowiedź (sort) | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/ehr-summary` | `ehr:read` | - | `EhrSummaryResponse` | 200; 404 |
| GET | `/encounters` | `ehr:read` | - | `EncounterResponse[]` (`startAt` malejąco, `id`) | 200; 404 |
| GET | `/episodes` | `ehr:read` | - | `TreatmentEpisodeResponse[]` (`startAt` malejąco) | 200; 404 |
| GET | `/clinical-notes` | `ehr:read` | - | `ClinicalNoteResponse[]` (`createdAt` malejąco) | 200; 404 |
| GET | `/clinical-notes/{noteId}` | `ehr:read` | - | `ClinicalNoteResponse` | 200; 404 pacjent/notatka (także notatka innego pacjenta lub zły format id) |
| POST | `/clinical-notes` | któreś z `ehr:note:write`, `ehr:note:write-nursing`, `ehr:note:write-consultation` | `ClinicalNoteCreateRequest` | `ClinicalNoteResponse` + `Location` | 201; 403 kategoria niedozwolona dla roli; 404; 422 |
| GET | `/diagnoses` | `ehr:read` lub `ehr:read-limited` | - | `DiagnosisResponse[]` (`diagnosedAt` malejąco) | 200; 404 |
| GET | `/diagnoses/{diagnosisId}` | `ehr:read` lub `ehr:read-limited` | - | `DiagnosisResponse` | 200; 404 |
| POST | `/diagnoses` | `ehr:diagnosis:write` | `DiagnosisCreateRequest` | `DiagnosisResponse` + `Location` | 201; 404; 422 |
| GET | `/allergies` | `ehr:read` lub `ehr:read-limited` | - | `AllergyResponse[]` (`recordedAt` malejąco) | 200; 404 |
| GET | `/allergies/{allergyId}` | `ehr:read` lub `ehr:read-limited` | - | `AllergyResponse` | 200; 404 |
| POST | `/allergies` | `ehr:allergy:write` | `AllergyCreateRequest` | `AllergyResponse` + `Location` | 201; 404; 422 |
| GET | `/contraindications` | `ehr:read` lub `ehr:read-limited` | - | `ContraindicationResponse[]` (`recordedAt` malejąco) | 200; 404 |
| GET | `/treatments` | `ehr:read` lub `ehr:read-limited` | - | `TreatmentResponse[]` (`startAt` malejąco) | 200; 404 |
| GET | `/dictionaries/icd-10` | uwierz. | query: `term`, `size` | `Coding[]` (`system="ICD-10"`, kod rosnąco) | 200; 422 `size` < 1 |

Ścieżki powyżej (poza słownikiem) są względem `/patients/{patientId}`. Nagłówek `Location` wskazuje `/api/v1/patients/{id}/<zasób>/{noweId}`, czyli na endpointy `GET` pojedynczego zasobu powyżej.

## `GET /ehr-summary` - skład projekcji

| Pole | Zawartość |
| --- | --- |
| `recentDiagnoses` | 5 najnowszych diagnoz (`diagnosedAt` malejąco) |
| `chronicConditions` | wszystkie diagnozy typu `chronic` (`diagnosedAt` malejąco) |
| `activeMedications` | pozycje aktywnych recept (`issued`/`partially_dispensed`, `validUntil` >= dziś UTC) - ten sam kształt co `GET /patients/{id}/active-medications` (`ActiveMedicationResponse`) |
| `recentEncounters` | 5 najnowszych kontaktów (`startAt` malejąco) |
| `allergies` | wszystkie alergie pacjenta (każdego statusu) |

## Zapis - reguły

Wspólne dla `POST`: aktor z tokenu (`authorId`/`diagnosedById`/`recordedById` z ciała ignorowane); `patientId` w ciele musi zgadzać się ze ścieżką (422 `mismatch`); `encounterId` (jeśli podany) musi należeć do pacjenta (422 `notFound`).

- **Notatka**: dozwolone kategorie zależą od uprawnień roli (suma): `ehr:note:write` (doctor) - wszystkie (`admission`, `progress`, `consultation`, `nursing`, `observation`, `discharge`); `ehr:note:write-nursing` (nurse) - `nursing`, `observation`; `ehr:note:write-consultation` (radiologist) - `consultation`. Inna kategoria -> 403 (sprawdzane przed istnieniem pacjenta). `title` i `content` przycinane; `symptoms` przycinane, bez pustych i duplikatów, zwracane alfabetycznie (pole pomijane, gdy puste). Zdarzenie `ClinicalNoteCreated`.
- **Diagnoza**: `code` (`Coding`: `system`, `code`, `display`) wymagany; `status` domyślnie `active`; `diagnosedAt` domyślnie teraz; `notes` puste -> `null`. Kod nie jest walidowany względem słownika (dowolny `CodingSystem`). Zdarzenie `DiagnosisRecorded`.
- **Alergia**: `status` domyślnie `active`; `recordedAt` domyślnie teraz; `atcCodes` przycinane/bez duplikatów (zwracane alfabetycznie, pomijane gdy puste). Zdarzenie `AllergyRecorded`. Aktywne alergie są odczytywane przez kontrolę bezpieczeństwa leku ([rest-api-prescription.md](rest-api-prescription.md#kontrola-bezpieczeństwa-leku)).
- Nie ma `PUT`/`PATCH`/`DELETE` na zasoby EHR; `contraindications` i `treatments` są tylko do odczytu (dane z migracji mock).

## Słownik ICD-10

`GET /dictionaries/icd-10?term=&size=`: wyszukiwanie po kodzie lub nazwie (wszystkie tokeny `term` muszą pasować, bez wielkości liter/diakrytyków), `size` domyślnie 50, maks. 100 (większe obcinane), wynik wg kodu rosnąco. Brak `term` zwraca pierwsze `size` kodów. Dane to ~40 kodów z `reference/002-icd10-code.sql` ([database-and-data.md](database-and-data.md)). SNOMED CT: osobny moduł, [terminology-snomed.md](terminology-snomed.md).
