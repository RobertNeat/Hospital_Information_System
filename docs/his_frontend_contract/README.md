# Kontrakt his_frontend: elementy niezaimplementowane

Ten katalog zawiera wyłącznie oczekiwania frontendu wobec backendu (oraz luki po stronie UI), które nie zostały jeszcze zrealizowane. Zaimplementowane modele i operacje opisują typy TypeScript (`apps/his_frontend/src/app/models/`, `models/api/`) i serwisy (`services/*.service.ts`). Kontrakt po stronie backendu: [`../his_backend_contract`](../his_backend_contract/README.md).

Zasady: po wdrożeniu pozycji usuwamy ją stąd; zmiana modelu TS jest ewolucyjna (bez zmiany nazw pól i wartości unii, nowe pola tylko opcjonalne).

## Luki w UI względem backendu

| Obszar | Stan |
| --- | --- |
| Podpowiedzi terminologii - wyszukiwanie | kreatory lab/obrazowania wołają `GET /terminology/snomed/suggestions?kind=diagnosis` bez `term` (pierwsza strona wg ECL specjalizacji, ok. 100 pozycji z dziesiątek tysięcy); brak pola wyszukiwania w pickerze, więc rzadsze rozpoznania są praktycznie nieosiągalne |

## Świadomie niewdrażane / do decyzji

- Załączniki DICOM, obrazy i pliki (brak `Attachment`, `studyInstanceUid`).
- Serwerowy PDF raportów (obecnie generowanie w przeglądarce).
- Automatyczny test zgodności TS <-> API; generowanie klienta z OpenAPI.

## Porządki w modelach

- Aliasy `*Draft` (`LabOrderDraft`, `ImagingOrderDraft`, `PrescriptionDraft`) są `@deprecated`; do usunięcia po przejściu na `*CreateRequest`.
- Modele zleceń, recept i notatek (`orderedById`, `prescriberId`, `authorId`) niosą pole aktora ignorowane przez backend.
