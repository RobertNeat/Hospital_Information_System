# REST API - indeks i zasady wspólne

Indeks 83 endpointów REST `his_backend` (liczba zweryfikowana grepem po `@GetMapping`/`@PostMapping`/`@PatchMapping`; w `/api/v1` brak `PUT` i `DELETE`; `PUT` istnieje tylko w `/fhir`, patrz [rest-api-fhir.md](rest-api-fhir.md)) oraz zasady czytania podplików. Powrót: [README.md](README.md).

## Zasady czytania

- Ścieżki względem `/api/v1` (tak jak w kodzie: `@RequestMapping("/api/v1...")`).
- **Uprawnienie** = wartość z `@PreAuthorize("hasAuthority(...)")`; role posiadające uprawnienie: [auth-and-security.md](auth-and-security.md#role-i-uprawnienia). "uwierz." = wystarczy ważny token, "publiczny" = bez tokenu.
- **Błędy wspólne** dla każdego chronionego endpointu: 401 `UNAUTHENTICATED`, 403 `FORBIDDEN`, 500 `INTERNAL` - w tabelach nie są powtarzane. W kolumnie "Statusy": kody sukcesu i błędy specyficzne (404 `NOT_FOUND`, 409 `CONFLICT`, 422 `VALIDATION_FAILED`). Format błędu: [conventions.md](conventions.md#błędy-problemdetail).
- Typy żądań i odpowiedzi to nazwy klas DTO z [data-types.md](data-types.md); w nawiasie typ TS tylko tam, gdzie nazwa się różni.
- "Pag." = odpowiedź `PageResponse<T>` ze `page`/`size`/`sort` ([conventions.md](conventions.md#paginacja)).
- Aktor operacji zawsze z tokenu; `patientId` w ciele (jeśli podany) musi zgadzać się ze ścieżką (422 `mismatch`).
- Brak pacjenta o `patientId` ze ścieżki (lub zły format UUID) -> 404 we wszystkich endpointach `/patients/{patientId}/...`.

## Podpliki

| Plik | Endpointy |
| --- | --- |
| [rest-api-auth-staff.md](rest-api-auth-staff.md) | `/auth/*` (5), `/staff` (4), `/wards` (1), `/dashboard/stats` (1) |
| [rest-api-patient.md](rest-api-patient.md) | `/patients*` (8) |
| [rest-api-ehr.md](rest-api-ehr.md) | `/patients/{id}/...` EHR (11), `/dictionaries/icd-10` (1) |
| [rest-api-lab.md](rest-api-lab.md) | katalog (2), zlecenia (5), wyniki (6) |
| [rest-api-imaging.md](rest-api-imaging.md) | katalog i sloty (2), zlecenia (5), wyniki (4) |
| [rest-api-prescription.md](rest-api-prescription.md) | `/drugs*` (2), recepty (5), `/drug-safety-checks` (1) |
| [rest-api-vitals.md](rest-api-vitals.md) | odczyty (3), `ward-overview` (1), `/vital-thresholds` (1) |
| [rest-api-messaging.md](rest-api-messaging.md) | wątki (6), zadania (3), przekazania (2), alerty (2) |
| [terminology-snomed.md](terminology-snomed.md) | `/terminology/snomed/*` (3) |
| [rest-api-fhir.md](rest-api-fhir.md) | `/fhir/MedicationRequest/{id}` (GET, PUT; poza `/api/v1`, klucz usługowy; nie wliczone do 83) |

## Pełna lista

| Metoda | Ścieżka | Uprawnienie | Plik |
| --- | --- | --- | --- |
| POST | `/auth/login` | publiczny | auth-staff |
| POST | `/auth/logout` | uwierz. | auth-staff |
| GET | `/auth/me` | uwierz. | auth-staff |
| POST | `/auth/register` | publiczny | auth-staff |
| GET | `/auth/register/wards` | publiczny | auth-staff |
| GET | `/staff` | `staff:read` | auth-staff |
| GET | `/staff/{staffId}` | `staff:read` | auth-staff |
| POST | `/staff/{staffId}/activate` | `account:manage` | auth-staff |
| POST | `/staff/{staffId}/lock` | `account:manage` | auth-staff |
| GET | `/wards` | `ward:read` | auth-staff |
| GET | `/dashboard/stats` | `dashboard:read` | auth-staff |
| GET | `/patients` | `patient:read` | patient |
| GET | `/patients/{patientId}` | `patient:read` | patient |
| POST | `/patients/duplicate-check` | `patient:read` | patient |
| POST | `/patients` | `patient:write` | patient |
| PATCH | `/patients/{patientId}` | `patient:write` | patient |
| GET | `/patients/{patientId}/admissions` | `admission:read` | patient |
| POST | `/patients/{patientId}/admissions` | `admission:admit` | patient |
| POST | `/patients/{patientId}/discharge` | `admission:discharge` | patient |
| GET | `/patients/{patientId}/ehr-summary` | `ehr:read` | ehr |
| GET | `/patients/{patientId}/encounters` | `ehr:read` | ehr |
| GET | `/patients/{patientId}/episodes` | `ehr:read` | ehr |
| GET | `/patients/{patientId}/clinical-notes` | `ehr:read` | ehr |
| POST | `/patients/{patientId}/clinical-notes` | `ehr:note:write*` | ehr |
| GET | `/patients/{patientId}/diagnoses` | `ehr:read` lub `ehr:read-limited` | ehr |
| POST | `/patients/{patientId}/diagnoses` | `ehr:diagnosis:write` | ehr |
| GET | `/patients/{patientId}/allergies` | `ehr:read` lub `ehr:read-limited` | ehr |
| POST | `/patients/{patientId}/allergies` | `ehr:allergy:write` | ehr |
| GET | `/patients/{patientId}/contraindications` | `ehr:read` lub `ehr:read-limited` | ehr |
| GET | `/patients/{patientId}/treatments` | `ehr:read` lub `ehr:read-limited` | ehr |
| GET | `/dictionaries/icd-10` | uwierz. | ehr |
| GET | `/lab-tests` | `lab-order:read` | lab |
| GET | `/lab-panels` | `lab-order:read` | lab |
| GET | `/lab-orders` | `lab-order:read` | lab |
| GET | `/lab-orders/{orderId}` | `lab-order:read` | lab |
| POST | `/patients/{patientId}/lab-orders` | `lab-order:create` | lab |
| POST | `/lab-orders/{orderId}/status` | `lab-order:update-status` lub `lab-order:collect-specimen` | lab |
| POST | `/lab-orders/{orderId}/cancel` | `lab-order:cancel` | lab |
| GET | `/patients/{patientId}/lab-results` | `lab-result:read` | lab |
| GET | `/patients/{patientId}/lab-results/trends/{analyteCode}` | `lab-result:read` | lab |
| GET | `/patients/{patientId}/lab-results/analytes` | `lab-result:read` | lab |
| GET | `/lab-results` | `lab-result:read` | lab |
| GET | `/lab-results/{resultId}` | `lab-result:read` | lab |
| POST | `/lab-results/{resultId}/acknowledge` | `lab-result:acknowledge` | lab |
| GET | `/imaging-exams` | `imaging-order:read` | imaging |
| GET | `/imaging-slots` | `imaging-order:read` | imaging |
| GET | `/imaging-orders` | `imaging-order:read` | imaging |
| GET | `/imaging-orders/{orderId}` | `imaging-order:read` | imaging |
| POST | `/patients/{patientId}/imaging-orders` | `imaging-order:create` | imaging |
| POST | `/imaging-orders/{orderId}/status` | `imaging-order:update-status` | imaging |
| POST | `/imaging-orders/{orderId}/cancel` | `imaging-order:cancel` | imaging |
| GET | `/patients/{patientId}/imaging-results` | `imaging-result:read` | imaging |
| GET | `/imaging-results` | `imaging-result:read` | imaging |
| GET | `/imaging-results/{resultId}` | `imaging-result:read` | imaging |
| POST | `/imaging-results/{resultId}/acknowledge` | `imaging-result:acknowledge` | imaging |
| GET | `/drugs` | `drug:read` | prescription |
| GET | `/drugs/{drugId}` | `drug:read` | prescription |
| GET | `/prescriptions` | `prescription:read` | prescription |
| GET | `/prescriptions/{prescriptionId}` | `prescription:read` | prescription |
| GET | `/patients/{patientId}/active-medications` | `prescription:read` | prescription |
| POST | `/patients/{patientId}/prescriptions` | `prescription:create` | prescription |
| POST | `/prescriptions/{prescriptionId}/cancel` | `prescription:cancel` | prescription |
| POST | `/drug-safety-checks` | `drug-safety-check:run` | prescription |
| GET | `/patients/{patientId}/vitals` | `vitals:read` | vitals |
| GET | `/patients/{patientId}/vitals/latest` | `vitals:read` | vitals |
| POST | `/patients/{patientId}/vitals` | `vitals:write` | vitals |
| GET | `/vitals/ward-overview` | `vitals:read` | vitals |
| GET | `/vital-thresholds` | `vital-threshold:read` | vitals |
| GET | `/message-threads` | `message:read` | messaging |
| GET | `/message-threads/{threadId}` | `message:read` | messaging |
| GET | `/message-threads/{threadId}/messages` | `message:read` | messaging |
| POST | `/message-threads` | `message:write` | messaging |
| POST | `/message-threads/{threadId}/messages` | `message:write` | messaging |
| POST | `/message-threads/{threadId}/read` | `message:write` | messaging |
| GET | `/tasks` | `task:read` | messaging |
| POST | `/tasks` | `task:write` | messaging |
| POST | `/tasks/{taskId}/status` | `task:write` | messaging |
| GET | `/handoff-notes` | `task:read` | messaging |
| POST | `/handoff-notes` | `task:write` | messaging |
| GET | `/alerts` | `alert:read` | messaging |
| POST | `/alerts/{alertId}/acknowledge` | `alert:acknowledge` | messaging |
| GET | `/terminology/snomed/concepts` | uwierz. | terminology |
| GET | `/terminology/snomed/concepts/{sctid}` | uwierz. | terminology |
| GET | `/terminology/snomed/suggestions` | uwierz. | terminology |

Endpointów zapisu, które istnieją w kontrakcie frontendu, a których nie ma w kodzie: brak. Zapisów, których nie ma w API REST: wyniki lab i obrazowe (tylko FHIR), progi parametrów życiowych, zmiana/usunięcie pracownika lub oddziału, edycja/anulowanie notatek, diagnoz i alergii.

## Operacje poza HTTP

| Operacja | Gdzie | Uwagi |
| --- | --- | --- |
| Zapis wyniku laboratoryjnego | `LabResultRecordingService.recordResult(RecordLabResultCommand)` | brak endpointu REST; wejście: `POST /fhir/DiagnosticReport` ([rest-api-fhir.md](rest-api-fhir.md)); reguły w [rest-api-lab.md](rest-api-lab.md#zapis-wyniku-poza-rest) |
| Zapis wyniku obrazowego | `ImagingResultRecordingService.recordResult(RecordImagingResultCommand)` | brak endpointu REST; wejście: `POST /fhir/DiagnosticReport` ([rest-api-fhir.md](rest-api-fhir.md#badania-obrazowe-e-imaging)); reguły w [rest-api-imaging.md](rest-api-imaging.md#zapis-wyniku-poza-rest) |
| Tworzenie alertów | `AlertService.raise` | tylko jako reakcja na zdarzenia ([events.md](events.md)) |
| Push STOMP | `RealtimePublisher` | [realtime-stomp.md](realtime-stomp.md) |
