# REST: parametry życiowe

`VitalsController` i `VitalThresholdController` (prefiks `/api/v1`). Powrót: [README.md](README.md) | indeks: [rest-api.md](rest-api.md). Typy: [data-types.md](data-types.md#parametry-życiowe).

| Metoda | Ścieżka | Uprawnienie | Parametry / body | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/patients/{patientId}/vitals` | `vitals:read` | query: `range` (`24h`/`7d`/`30d`/`all`, domyślnie `all`) | `VitalSignsResponse[]` (rosnąco wg `recordedAt`, potem `id`) | 200; 404; 422 zły `range` |
| GET | `/patients/{patientId}/vitals/latest` | `vitals:read` | - | `VitalSignsResponse` albo brak treści | 200; **204** brak odczytów; 404 pacjent |
| POST | `/patients/{patientId}/vitals` | `vitals:write` | `VitalSignsCreateRequest` | `VitalsRecordResponse` (`saved` + `anomalies`) | 201 (bez `Location`); 403; 404; 422 |
| GET | `/vitals/ward-overview` | `vitals:read` | query: `wardId` (opcjonalnie) | `WardVitalsRow[]` | 200; 404 oddział (gdy podano `wardId`); 422 |
| GET | `/vital-thresholds` | `vital-threshold:read` | - | `VitalThresholdResponse[]` (kolejność `VitalType`) | 200 |

Okno `range` liczone wstecz od "teraz" (włącznie z początkiem okna). Brak endpointu zapisu progów (`vital-threshold:write` bez endpointu).

## Zapis odczytu - walidacja (422, `errors[].field` = nazwa pomiaru)

- Wymagany co najmniej jeden pomiar (`field="measurements"`, `code="required"`) spośród `systolic`, `diastolic`, `heartRate`, `spo2`, `respiratoryRate`, `temperature`, `painScore`. Wymagany `context`.
- Pomiary (poza `temperature`) muszą być liczbami całkowitymi (`invalidFormat`); `temperature` ma najwyżej 1 miejsce po przecinku.
- Wartość poza zakresem `min`/`max` progu danego parametru -> 422 (`code="range"`, komunikat z zakresem i jednostką). Nazwy pól: `systolic`, `diastolic`, `heartRate`, `temperature`, `spo2`, `respiratoryRate`.
- `painScore`: liczba całkowita 0-10 (brak progów w `vital_threshold`).
- `recordedAt` domyślnie teraz; nie może być z przyszłości (tolerancja 60 s).
- `deviceId` tylko przy `source="monitor"` (domyślnie `source="manual"`); do 50 znaków.
- `encounterId` musi należeć do pacjenta; `patientId` w ciele zgodny ze ścieżką.
- `recordedById` ignorowane (aktor z tokenu); brak powiązania sesji z pracownikiem -> 403.
- Odczyty są niezmienne (korekta = nowy odczyt); brak `PUT`/`DELETE`.

## Anomalie (liczone przez backend z tabeli `vital_threshold`)

Porównania są **ścisłe**: wartość równa progowi jest jeszcze "w normie" / "jeszcze nie krytyczna". Dla każdego pomiaru, który ma próg (kolejność wyniku = kolejność `VitalType`: `systolic`, `diastolic`, `heartRate`, `temperature`, `spo2`, `respiratoryRate`):

| Warunek | `severity` | `direction` |
| --- | --- | --- |
| `value < criticalLow` | `critical` | `low` |
| `value > criticalHigh` | `critical` | `high` |
| `value < low` | `warning` | `low` |
| `value > high` | `warning` | `high` |
| w przeciwnym razie | brak anomalii | - |

- Pomiary nieobecne, `painScore` i typy bez progu są pomijane. `message` (po polsku) ma postać `"<etykieta>: wartość krytycznie niska|wysoka|poniżej normy|powyżej normy (<wartość> <jednostka>)."`; `recordedAt` = czas odczytu.
- `anomalies` w `VitalsRecordResponse` zawiera wszystkie anomalie (`warning` i `critical`). Zdarzenie `VitalAnomalyDetected` powstaje **tylko** gdy jest co najmniej jedna `critical` (niesie wyłącznie krytyczne) -> alert `vital_anomaly` ([events.md](events.md)). Anomalia `warning` nie tworzy alertu.
- Anomalie nigdy nie są zapisywane; liczone przy zapisie i przy przeglądzie oddziału.
- Domyślne progi (`reference/001-vital-threshold.sql`): 6 wierszy, po jednym na `VitalType`; wartości w bazie, nie w kodzie ([database-and-data.md](database-and-data.md)).

## Przegląd oddziału (`ward-overview`)

- Pacjenci ze statusem `admitted` i **aktywnym** przyjęciem (na oddział `wardId` albo wszystkie oddziały, gdy brak parametru). Pacjenci `outpatient` nie są uwzględniani.
- Wiersz: `patient` (`PatientSummaryResponse`), `latest` (ostatni odczyt; pomijany, gdy brak), `anomalies` (z ostatniego odczytu; `[]` gdy brak), `lastMeasuredAgoMin` (minuty od `latest.recordedAt`, min. 0; pomijane gdy brak odczytu).
- Kolejność: 1) wiersze z anomalią `critical`, 2) z samymi `warning`, 3) bez anomalii; w grupie: więcej anomalii najpierw, potem nazwisko, imię, `id`.
- Ten sam widok zasila licznik `vitalsAnomalies` w `/dashboard/stats`.
