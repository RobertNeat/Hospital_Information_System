# Kontrakt his_backend: elementy niezaimplementowane

Ten katalog zawiera wyłącznie to, czego `apps/his_backend` jeszcze nie wystawia lub nie egzekwuje względem oczekiwań frontendu ([`../his_frontend_contract`](../his_frontend_contract/README.md)). Zaimplementowane kontrakty (REST `/api/v1`, STOMP, FHIR, auth, zdarzenia) opisuje kod: kontrolery, DTO, `@PreAuthorize`, `RolePermissions`, `GlobalExceptionHandler`, `SecurityConfig`, `realtime/*`, `db/changelog`.

Zasady utrzymania: po wdrożeniu pozycji usuwamy ją stąd; nie dopisujemy opisów istniejącej funkcjonalności. Bieżące prace i usterki: [`../currentwork/plan.md`](../currentwork/plan.md).

## Brakujące endpointy i egzekwowanie uprawnień

| Obszar | Oczekiwanie / stan | Brak |
| --- | --- | --- |
| Zapis słowników | admin "W" dla `staff`, `wards`, `vital-thresholds` | brak endpointów zapisu; `staff:write`, `ward:write`, `vital-threshold:write` są w tokenie, ale żaden kontroler ich nie sprawdza |
| Wprowadzanie wyników | laborant/radiolog "W" wyników | brak `POST` wyniku w REST; zapis tylko przez `POST /fhir/DiagnosticReport` (e-laboratory, e-imaging); `lab-result:write`, `imaging-result:write` nieegzekwowane |
| `drug-safety-checks` | nurse, pharmacist, admin "R" | `drug-safety-check:run` ma tylko `doctor` |
| `acknowledge` wyniku lab/imaging | 409 przy niezgodnym `version` | `version` ignorowane (brak kolumny) |

## Sesja i bezpieczeństwo

- Brak odwoływania i odświeżania tokenów JWT; blokada konta lub zmiana roli nie działa na wydane tokeny do `exp`.
- STOMP: ważność tokenu sprawdzana tylko przy CONNECT; rejestr obecności (`StaffMember.online`) w pamięci jednej instancji, bez pushu zmian obecności; broker w pamięci (bez wielu instancji, bez trwałych kolejek).
- Brak rate limitingu poza blokadą kont.
- Konta demo (`db/changelog/demo/`, kontekst `reference`) ładują się także na produkcji; konieczna zmiana haseł po wdrożeniu.
- Niezweryfikowane uruchomieniem: czy `anyRequest().denyAll()` daje 401 czy 403 poza `/api/**`, `/ws/**`, `/actuator/health/**`.

## Integracje FHIR (e-receipt, e-laboratory, e-imaging)

- Brak ponawiania nieudanej wysyłki zleceń/recept (best effort, AFTER_COMMIT; błędy tylko logowane).
- Zmiany stanu zleceń wykonane w HIS (poza anulowaniem) nie są przekazywane do e-laboratory ani e-imaging.
- Odpowiedź `201` wystawienia recepty zawiera klucz lokalny; właściwy `eRxKey` z e-receipt zapisuje się po commicie (plan.md, P1).
- Wygasanie recept wyliczane przy odczycie; brak schedulera i zdarzenia wygaśnięcia.

## Zdarzenia i alerty

- Zdarzenia bez konsumenta (punkty rozszerzeń): `PatientAdmitted`, `PatientDischarged`, `ClinicalNoteCreated`, `DiagnosisRecorded`, `AllergyRecorded`.
- `AlertType.system` i `AlertTargetKind.patient` nie są przez nic tworzone.
- Brak alertów dla: przyjęcia/wypisu, notatek, diagnoz, alergii, recept, wiadomości, zmiany statusu zadania, anomalii `warning`.

## Terminologia SNOMED

- Składnia ECL profili `his.terminology.suggestions.*` niesprawdzona na lokalnym Snowstorm Lite (plan.md, P5, P6).
- Relacja `GET /dictionaries/icd-10` (~40 kodów w SQL) do SNOMED/SCTID otwarta; walidacja zapisywanych kodów sprawdza tylko format SCTID, nie zgodność ze specjalizacją.

## Paginacja

- `GET /message-threads/{id}/messages` i `GET /alerts` zwracają `T[]` bez paginacji (kandydaci do paginacji/kursora `before=sentAt`).
