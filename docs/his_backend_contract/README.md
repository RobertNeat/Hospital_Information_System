# Kontrakt his_backend: elementy niezaimplementowane

Ten katalog zawiera wyłącznie to, czego `apps/his_backend` jeszcze nie wystawia lub nie egzekwuje względem oczekiwań frontendu ([`../his_frontend_contract`](../his_frontend_contract/README.md)). Zaimplementowane kontrakty (REST `/api/v1`, STOMP, FHIR, auth, zdarzenia) opisuje kod: kontrolery, DTO, `@PreAuthorize`, `RolePermissions`, `GlobalExceptionHandler`, `SecurityConfig`, `realtime/*`, `db/changelog`.

Zasady utrzymania: po wdrożeniu pozycji usuwamy ją stąd; nie dopisujemy opisów istniejącej funkcjonalności. Bieżące prace i usterki: [`../currentwork/plan.md`](../currentwork/plan.md).

## Sesja i bezpieczeństwo

- Brak odwoływania i odświeżania tokenów JWT; blokada konta lub zmiana roli nie działa na wydane tokeny do `exp`.
- STOMP: ważność tokenu sprawdzana tylko przy CONNECT; rejestr obecności (`StaffMember.online`) w pamięci jednej instancji, bez pushu zmian obecności; broker w pamięci (bez wielu instancji, bez trwałych kolejek).
- Brak rate limitingu poza blokadą kont.
- Konta demo (`db/changelog/demo/`, kontekst `reference`) ładują się także na produkcji; konieczna zmiana haseł po wdrożeniu.
- Niezweryfikowane uruchomieniem: czy `anyRequest().denyAll()` daje 401 czy 403 poza `/api/**`, `/ws/**`, `/actuator/health/**`.

## Integracje FHIR (e-receipt, e-laboratory, e-imaging)

- Brak ponawiania nieudanej wysyłki zleceń/recept (best effort, AFTER_COMMIT; błędy tylko logowane). Wymagałoby to
  nowej tabeli (outbox wysyłek) i schedulera, którego obecnie w ogóle nie ma w `his_backend` (brak `@Scheduled`/
  `@EnableScheduling`). e-receipt/e-laboratory/e-imaging są idempotentne względem identyfikatora zlecenia/recepty z HIS
  przy `POST` (powtórne wywołanie zwraca istniejący rekord, ten sam `eRxKey`), więc retry samego `POST` jest
  bezpieczny; pozostaje do rozstrzygnięcia kolejność ponowień względem późniejszych `PUT` zmiany statusu (odrzucone
  404, dopóki `POST` się nie powiedzie) oraz to, że `onIssued` (e-receipt) podmienia `erx_key` w tym samym żądaniu
  HTTP (osobna transakcja `REQUIRES_NEW`, ale przed odpowiedzią do klienta) - opóźniony retry podmieniłby klucz już
  po tym, jak klient zobaczył lokalny.
  Zakres większy niż punktowa poprawka; do zaplanowania osobno.
- Wygasanie recept wyliczane przy odczycie (`Prescription#effectiveStatus`); brak schedulera i zdarzenia wygaśnięcia.
  Pozostawione świadomie: wymagałoby tego samego schedulera co ponawianie wysyłki (patrz wyżej), e-receipt jest już
  w stanie wymusić `expired` na recepcie przez `PUT /fhir/MedicationRequest/{id}` (`PrescriptionExternalService`,
  `applyExternalStatus`), a zdarzenie wygaśnięcia nie miałoby obecnie żadnego konsumenta (brak alertów dla recept,
  patrz "Zdarzenia i alerty").

## Zdarzenia i alerty

- Zdarzenia bez konsumenta (punkty rozszerzeń): `PatientAdmitted`, `PatientDischarged`, `ClinicalNoteCreated`, `DiagnosisRecorded`, `AllergyRecorded`.
- `AlertType.system` i `AlertTargetKind.patient` nie są przez nic tworzone.
- Brak alertów dla: przyjęcia/wypisu, notatek, diagnoz, alergii, recept, wiadomości, zmiany statusu zadania, anomalii `warning`.

## Terminologia SNOMED

- Walidacja zapisywanych kodów sprawdza tylko format SCTID, nie zgodność ze specjalizacją.
