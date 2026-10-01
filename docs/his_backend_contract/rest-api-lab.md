# REST: laboratorium

Katalog badań, zlecenia i wyniki laboratoryjne (`LabCatalogController`, `LabOrderController`, `LabResultController`; prefiks `/api/v1`). Powrót: [README.md](README.md) | indeks: [rest-api.md](rest-api.md). Typy: [data-types.md](data-types.md#laboratorium).

## Katalog

| Metoda | Ścieżka | Uprawnienie | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- |
| GET | `/lab-tests` | `lab-order:read` | `LabTestResponse[]` z `analytes` (alfabetycznie wg nazwy, kolacja pl-PL; anality wg `code`) | 200 |
| GET | `/lab-panels` | `lab-order:read` | `LabPanelResponse[]` (alfabetycznie wg nazwy; `testCodes` alfabetycznie) | 200 |

Katalogi są danymi z migracji (mock), bez endpointów zapisu.

## Zlecenia

| Metoda | Ścieżka | Uprawnienie | Parametry / body | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/lab-orders` | `lab-order:read` | query: `patientId`, `status`, `urgency`, `orderedFrom`, `orderedTo` (ISO-8601, włącznie, po `orderedAt`), `page`, `size`, `sort` | Pag. `LabOrderResponse` | 200; 422 |
| GET | `/lab-orders/{orderId}` | `lab-order:read` | - | `LabOrderResponse` | 200; 404 |
| POST | `/patients/{patientId}/lab-orders` | `lab-order:create` | `LabOrderCreateRequest` | `LabOrderResponse` + `Location: /api/v1/lab-orders/{id}` | 201; 404 pacjent; 422 |
| POST | `/lab-orders/{orderId}/status` | `lab-order:update-status` lub `lab-order:collect-specimen` | `OrderStatusUpdateRequest` (`status`, `note?`, `version?`) | `LabOrderResponse` | 200; 403; 404; 409; 422 |
| POST | `/lab-orders/{orderId}/cancel` | `lab-order:cancel` | `OrderCancelRequest` (`reason` wymagany, `version?`) | `LabOrderResponse` | 200; 404; 409; 422 |

`sort`: `orderedAt`, `plannedCollectionAt`, `status`, `urgency`, `createdAt`; domyślnie `orderedAt` malejąco.

### Tworzenie zlecenia

- Status początkowy `ordered`; `statusHistory` zawiera wpis `ordered` (aktor = zlecający z tokenu).
- 422 (z `errors[]`):
  - `items` puste; `items[i].testCode` nie istnieje w katalogu (`notFound`) lub powtórzony (`duplicate`);
  - `items[i].specimenType` niedozwolony dla badania (`notAllowed`);
  - `fasting` nie jest `true`, gdy którekolwiek zlecone badanie ma `fastingRequired` (`fastingRequired`);
  - `encounterId` nie należy do pacjenta (`notFound`); `patientId` w ciele niezgodny ze ścieżką (`mismatch`);
  - brakujące `urgency`, `fasting`, `plannedCollectionAt`, `clinicalInfo`.
- `testName` zapisywane jako snapshot z katalogu (wartość z żądania ignorowana).

### Maszyna stanów zlecenia laboratoryjnego

`completed` i `cancelled` są końcowe. Niedozwolone przejście -> 409 `CONFLICT`.

| Z \ Na | `scheduled` | `specimen_collected` | `in_progress` | `completed` | `cancelled` |
| --- | --- | --- | --- | --- | --- |
| `ordered` | tak | tak | nie | nie | tak (`/cancel`) |
| `scheduled` | - | tak | nie | nie | tak (`/cancel`) |
| `specimen_collected` | nie | - | tak | tak | tak (`/cancel`) |
| `in_progress` | nie | nie | - | tak | tak (`/cancel`) |
| `completed` | nie | nie | nie | - | nie |
| `cancelled` | nie | nie | nie | nie | - |

- Status `ordered` nie jest celem żadnego przejścia.
- `POST .../status` z `status="cancelled"` -> 422 ("uzyj akcji /cancel"); anulowanie wyłącznie przez `/cancel` (wymaga `reason`, zapisywany jako `note` historii).
- Rola z `lab-order:update-status` (lab_technician, admin) ustawia dowolny dozwolony status; rola tylko z `lab-order:collect-specimen` (nurse) wyłącznie `specimen_collected` (inny -> 403).
- Niezgodne `version` -> 409. Każda zmiana dopisuje wpis `statusHistory` (`status`, `at`, `byId`, `note`) i publikuje `LabOrderStatusChanged`.
- Zlecenie `completed`/`cancelled` w `/cancel` -> 409.

## Wyniki

| Metoda | Ścieżka | Uprawnienie | Parametry | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/patients/{patientId}/lab-results` | `lab-result:read` | query: `filter` (`all`/`abnormal`/`critical`) | `LabResultResponse[]` (`resultedAt` malejąco, bez paginacji) | 200; 404; 422 |
| GET | `/patients/{patientId}/lab-results/trends/{analyteCode}` | `lab-result:read` | - | `AnalyteTrendResponse` | 200; 404 pacjent |
| GET | `/patients/{patientId}/lab-results/analytes` | `lab-result:read` | - | `LabAnalyteRef[]` (`code`, `name`) | 200; 404 |
| GET | `/lab-results` | `lab-result:read` | query: `patientId`, `filter`, `page`, `size`, `sort` | Pag. `LabResultWithPatientResponse` | 200; 422 |
| GET | `/lab-results/{resultId}` | `lab-result:read` | - | `LabResultResponse` | 200; 404 |
| POST | `/lab-results/{resultId}/acknowledge` | `lab-result:acknowledge` | opcjonalne body `ResultAcknowledgeRequest` (`version` ignorowane) | `LabResultResponse` | 200; 404 |

- Inbox `GET /lab-results`: pola wyniku spłaszczone + `patient` (`PatientSummaryResponse`; może być pominięte, jeśli pacjent zniknął); `sort`: `resultedAt`, `collectedAt`, `testCode`, `testName`, `category`, `status`; domyślnie `resultedAt` malejąco.
- Filtr: `abnormal` = którakolwiek obserwacja z flagą różną od `N`; `critical` = którakolwiek obserwacja `LL`/`HH`.
- `LabResultResponse.observations` posortowane po `analyteCode`; `Observation.value` to liczba (JSON number) albo tekst (string) zależnie od tego, która wartość jest zapisana; `referenceRange` zawsze obecny (może być `{}`).
- **Trend** (`trends/{analyteCode}`): punkty tylko z obserwacji liczbowych tego analitu pacjenta, rosnąco wg `collectedAt` (`at` = `collectedAt`, `value`, `flag`). Nagłówek (`analyteName`, `unit`, `low`, `high`) z najnowszego punktu; bez punktów - z definicji w katalogu; gdy kodu nie ma w katalogu: `analyteName` = kod, `unit` = `""`, `low`/`high` pominięte, `points=[]` (bez 404 dla nieznanego analitu).
- `analytes`: anality pacjenta z wartością liczbową, wg nazwy.
- **Acknowledge**: idempotentne; pierwsze potwierdzenie zapisuje `reviewedAt` i `reviewedById` (aktor z tokenu), kolejne zwracają wynik bez zmian. Brak 409 (wynik nie ma wersji).

## Zapis wyniku poza REST

Brak endpointu `POST` wyniku w `/api/v1`. Wynik wchodzi przez `LabResultRecordingService.recordResult(RecordLabResultCommand)`, wywoływany z `POST /fhir/DiagnosticReport` (usługa `e-laboratory`, klucz usługowy; mapowanie i idempotencja: [rest-api-fhir.md](rest-api-fhir.md#badania-laboratoryjne-e-laboratory)). Reguły zapisu:

| Reguła | Skutek |
| --- | --- |
| Wymagane: `patientId` (istnieje), `testCode` (istnieje w katalogu), `collectedAt`, `resultedAt` (nie wcześniej niż `collectedAt`), `status`, min. 1 obserwacja | inaczej 422 |
| Obserwacja: `analyteCode` należy do badania, bez duplikatów; dokładnie jedna wartość (liczbowa albo tekstowa, tekst do 500 znaków, liczba o module mniejszym niż 1E10, zaokrąglana do 4 miejsc) | inaczej 422 |
| Zlecenie: `orderItemId` wystarcza (zlecenie wynika z pozycji); samo `orderId` + `testCode` wskazuje pozycję; pacjent i badanie muszą zgadzać się ze zleceniem | inaczej 422; brak zlecenia = wynik zewnętrzny |
| Status zlecenia | `specimen_collected` lub `in_progress` przyjmują wynik; `completed` tylko korektę `corrected`; `ordered`/`scheduled`/`cancelled` -> 409 |
| Pozycja ma już wynik `final`/`corrected` | wtedy dozwolona tylko korekta `corrected` (inaczej 409) |
| Flaga obserwacji | jawna z polecenia (np. `LL`/`HH`); inaczej liczba -> `L`/`H`/`N` względem zakresu katalogu, tekst -> `N` (`LL`/`HH` tylko jawnie) |
| `performerName` | z polecenia, inaczej imię zalogowanego pracownika; brak obu -> 422 |
| Zdarzenie | `LabResultRecorded` (`critical` gdy któraś flaga `LL`/`HH`) -> alert ([events.md](events.md)) |
| Auto-`completed` | gdy wynik jest zatwierdzony (`final`/`corrected`) i **wszystkie** pozycje zlecenia mają zatwierdzony wynik, zlecenie przechodzi do `completed` (note "Wszystkie wyniki zatwierdzone (automatycznie)", aktor = rejestrujący lub systemowy `null`) i publikuje `LabOrderStatusChanged` |

Zlecenie nowo utworzone (`POST /patients/{id}/lab-orders`) jest po commicie wysyłane do e-laboratory (gdy integracja włączona), a anulowanie w HIS (`/cancel`) przekazywane; stan zlecenia zmienia też e-laboratory przez `PUT /fhir/ServiceRequest/{id}` z zachowaniem tej samej maszyny stanów ([rest-api-fhir.md](rest-api-fhir.md#badania-laboratoryjne-e-laboratory)).
