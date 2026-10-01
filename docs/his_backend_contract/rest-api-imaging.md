# REST: obrazowanie

Katalog badań, sloty, zlecenia i wyniki obrazowe (`ImagingCatalogController`, `ImagingOrderController`, `ImagingResultController`; prefiks `/api/v1`). Powrót: [README.md](README.md) | indeks: [rest-api.md](rest-api.md). Typy: [data-types.md](data-types.md#obrazowanie).

## Katalog i sloty

| Metoda | Ścieżka | Uprawnienie | Parametry | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/imaging-exams` | `imaging-order:read` | query: `modality` (opcjonalnie) | `ImagingExamResponse[]` (wg kolejności enuma modalności, nazwy, kodu) | 200; 422 zły enum |
| GET | `/imaging-slots` | `imaging-order:read` | query: `modality` i `date` (`YYYY-MM-DD`) - **wymagane** | `ScheduleSlotResponse[]` (`start`, potem `room`, `id`) | 200; 422 brak/zła wartość |

`/imaging-slots` zwraca **wszystkie** sloty modalności o początku w dobie `date` w strefie `Europe/Warsaw` (także zajęte - `available=false`); `start`/`end` w UTC. Katalog badań i sloty pochodzą z migracji (mock); brak endpointów zapisu.

## Zlecenia

| Metoda | Ścieżka | Uprawnienie | Parametry / body | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/imaging-orders` | `imaging-order:read` | query: `patientId`, `status`, `urgency`, `modality`, `page`, `size`, `sort` | Pag. `ImagingOrderResponse` | 200; 422 |
| GET | `/imaging-orders/{orderId}` | `imaging-order:read` | - | `ImagingOrderResponse` | 200; 404 |
| POST | `/patients/{patientId}/imaging-orders` | `imaging-order:create` | `ImagingOrderCreateRequest` | `ImagingOrderResponse` + `Location: /api/v1/imaging-orders/{id}` | 201; 404 pacjent; 409 slot zajęty; 422 |
| POST | `/imaging-orders/{orderId}/status` | `imaging-order:update-status` | `OrderStatusUpdateRequest` | `ImagingOrderResponse` | 200; 404; 409; 422 |
| POST | `/imaging-orders/{orderId}/cancel` | `imaging-order:cancel` | `OrderCancelRequest` (`reason` wymagany, `version?`) | `ImagingOrderResponse` | 200; 404; 409; 422 |

`sort`: `orderedAt`, `scheduledAt`, `status`, `urgency`, `modality`, `createdAt`; domyślnie `orderedAt` malejąco.

### Tworzenie zlecenia

- Snapshot z katalogu: `examName`, `modality`, `bodyRegion` (wartości z żądania ignorowane). `scheduledAt` wynika ze slotu.
- `laterality` pominięte = `na`; dla badań z `requiresLaterality` wartość `na` -> 422 (`laterality`, `required`).
- `contrast=true` dla badania bez `contrastPossible` -> 422 (`contrast`, `notAllowed`).
- `safety.confirmed` musi być `true` -> inaczej 422 (`safety.confirmed`, `required`). `safety.creatinine` (>= 0, do 2 miejsc) i `safety.egfr` (>= 0, do 1 miejsca) opcjonalne.
- `examCode` nie istnieje -> **422** (`notFound`), nie 404.
- `slotId` (opcjonalny): nie istnieje -> 422 (`notFound`); modalność slotu różna od badania -> 422 (`modalityMismatch`); slot zajęty (`available=false` albo istnieje na niego zlecenie w statusie innym niż `cancelled`) lub wyścig o slot -> 409 "Slot jest juz zajety".
- Bez `slotId`: zlecenie w statusie `ordered`, historia `ordered`. Ze `slotId`: slot rezerwowany (`available=false`), zlecenie od razu `scheduled` (historia: `ordered`, `scheduled`).

### Maszyna stanów zlecenia obrazowego

Osobna niż laboratoryjna (`specimen_collected` nie występuje). `completed`/`cancelled` końcowe. Niedozwolone przejście -> 409.

| Z \ Na | `scheduled` | `in_progress` | `completed` | `cancelled` |
| --- | --- | --- | --- | --- |
| `ordered` | tak | tak | nie | tak (`/cancel`) |
| `scheduled` | - | tak | tak | tak (`/cancel`) |
| `in_progress` | nie | - | tak | tak (`/cancel`) |
| `completed` | nie | nie | - | nie |
| `cancelled` | nie | nie | nie | - |

- `POST .../status` z `specimen_collected` -> 422 ("nie dotyczy zleceń obrazowych"); z `cancelled` -> 422 (użyj `/cancel`).
- Niezgodne `version` -> 409. `/cancel` zwalnia zarezerwowany slot (`available=true`).
- Każda zmiana dopisuje wpis `statusHistory` i publikuje `ImagingOrderStatusChanged`.
- Zmianę statusu mają `radiologist` i `admin` (`imaging-order:update-status`); nurse nie ma `imaging-order:*`.

## Wyniki

| Metoda | Ścieżka | Uprawnienie | Parametry | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/patients/{patientId}/imaging-results` | `imaging-result:read` | query: `filter` (`all`/`abnormal`/`critical`) | `ImagingResultResponse[]` (`reportedAt` malejąco, bez paginacji) | 200; 404; 422 |
| GET | `/imaging-results` | `imaging-result:read` | query: `patientId`, `filter`, `page`, `size`, `sort` | Pag. `ImagingResultWithPatientResponse` | 200; 422 |
| GET | `/imaging-results/{resultId}` | `imaging-result:read` | - | `ImagingResultResponse` | 200; 404 |
| POST | `/imaging-results/{resultId}/acknowledge` | `imaging-result:acknowledge` | opcjonalne body `ResultAcknowledgeRequest` (`version` ignorowane) | `ImagingResultResponse` | 200; 404 |

- Inbox `sort`: `reportedAt`, `performedAt`, `modality`, `examName`, `status`, `critical`; domyślnie `reportedAt` malejąco.
- Wynik obrazowy nie ma nasilenia zmian: `critical` (bool) jest jedyną flagą, dlatego `filter=abnormal` i `filter=critical` zawężają tak samo do `critical = true`.
- Acknowledge: idempotentne; ustawia `reviewedAt`/`reviewedById` raz; brak 409.
- Brak plików/DICOM; wynik niesie tylko `imageCount`.

## Zapis wyniku poza HTTP

Brak endpointu `POST` wyniku. `ImagingResultRecordingService.recordResult(RecordImagingResultCommand)` (dla radiologa lub usługi `e-imaging`):

| Reguła | Skutek |
| --- | --- |
| Wymagane: `patientId` (istnieje), `performedAt`, `reportedAt` (nie wcześniej niż `performedAt`), `findings`, `conclusion`, `status`; `imageCount` >= 0 (domyślnie 0) | inaczej 422 |
| Wynik zewnętrzny (`orderId=null`) | wymaga `modality`, `examName` (do 200), `bodyRegion` (do 100) |
| Wynik zlecenia | snapshot ze zlecenia; podana `modality` musi zgadzać się ze zleceniem; pacjent musi zgadzać się ze zleceniem (422) |
| Status zlecenia | zlecenie musi być `scheduled` lub `in_progress` (inaczej 409); zlecenie z wynikiem `final` nie przyjmuje kolejnego (409) |
| `radiologistName` | z polecenia, inaczej imię radiologa (`radiologistId`) lub zalogowanego pracownika; brak -> 422 |
| `critical` | ustawia radiolog (nie jest wyliczane z opisu) |
| Zdarzenie | `ImagingResultRecorded` (`critical`) -> alert ([events.md](events.md)) |
| Auto-`completed` | wynik `final` przenosi zlecenie do `completed` (note "Wynik ostateczny zapisany (automatycznie)"), publikuje `ImagingOrderStatusChanged`; wynik `preliminary` nie |
