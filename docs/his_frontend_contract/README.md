# Kontrakt his_frontend: elementy niezaimplementowane

Ten katalog zawiera wyłącznie oczekiwania frontendu wobec backendu (oraz luki po stronie UI), które nie zostały jeszcze zrealizowane. Zaimplementowane modele i operacje opisują typy TypeScript (`apps/his_frontend/src/app/models/`, `models/api/`) i serwisy (`services/*.service.ts`). Kontrakt po stronie backendu: [`../his_backend_contract`](../his_backend_contract/README.md).

Zasady: po wdrożeniu pozycji usuwamy ją stąd; zmiana modelu TS jest ewolucyjna (bez zmiany nazw pól i wartości unii, nowe pola tylko opcjonalne).

## Luki w UI względem backendu

| Obszar | Stan |
| --- | --- |
| Diagnozy i alergie | backend ma `POST /patients/{id}/diagnoses` i `/allergies`; `EhrService` nie ma metod zapisu, UI ich nie woła |
| Aktywacja/blokada kont | backend ma `POST /staff/{id}/activate` i `/lock`; brak typu TS, metody serwisu i widoku administratora |
| Podpowiedzi terminologii | `GET /terminology/snomed/suggestions?kind=` niewykorzystane przez frontend |
| Uprawnienia w UI | widoki i wywołania nie są w pełni bramkowane uprawnieniami z tokenu; role bez uprawnień dostają 403 i nieobsłużone `ApiError` (plan.md, P3) |
| Klucz e-recepty | modal po wystawieniu pokazuje klucz lokalny z odpowiedzi 201 (plan.md, P1); kreator ma nieaktualną notkę o integracji FHIR (P2) |
| Lista recept i kontekst pacjenta | kolumna "Pacjent" pokazuje UUID zamiast nazwiska; `his.currentPatientId` (sessionStorage) nie jest czyszczone przy wylogowaniu, tylko przyciskiem w nagłówku (P4) |

## Oczekiwania bez pokrycia w backendzie

Szczegóły: [`../his_backend_contract/README.md`](../his_backend_contract/README.md).

- Zapis `staff`, `wards`, `vital-thresholds` przez admina; wprowadzanie wyników lab/obrazowych przez laboranta/radiologa (UI poza kontraktem).
- `drug-safety-checks` dla `nurse`/`pharmacist`/`admin` (odczyt).
- `409` przy niezgodnym `version` w `acknowledge` wyników.
- Paginacja `getMessages` i `getAlerts`.

## Świadomie niewdrażane / do decyzji

- Załączniki DICOM, obrazy i pliki (brak `Attachment`, `studyInstanceUid`).
- Serwerowy PDF raportów (obecnie generowanie w przeglądarce).
- Automatyczny test zgodności TS <-> API; generowanie klienta z OpenAPI.

## Porządki w modelach

- Aliasy `*Draft` (`LabOrderDraft`, `ImagingOrderDraft`, `PrescriptionDraft`) są `@deprecated`; do usunięcia po przejściu na `*CreateRequest`.
- Modele zleceń, recept i notatek (`orderedById`, `prescriberId`, `authorId`) niosą pole aktora ignorowane przez backend.
