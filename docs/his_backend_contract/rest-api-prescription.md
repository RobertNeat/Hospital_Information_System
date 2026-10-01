# REST: leki, recepty, kontrola bezpieczeństwa leku

`DrugController`, `PrescriptionController` (prefiks `/api/v1`). Powrót: [README.md](README.md) | indeks: [rest-api.md](rest-api.md). Typy: [data-types.md](data-types.md#leki-i-recepty).

## Katalog leków

| Metoda | Ścieżka | Uprawnienie | Parametry | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/drugs` | `drug:read` | query: `term` | `DrugResponse[]` (max **50**, wg nazwy rosnąco, potem `id`; bez paginacji) | 200 |
| GET | `/drugs/{drugId}` | `drug:read` | - | `DrugResponse` | 200; 404 (także zły format UUID) |

`term`: każdy token (podział po białych znakach) musi być podciągiem nazwy handlowej, substancji czynnej albo kodu ATC (bez wielkości liter i diakrytyków). Brak/pusty `term` = pierwsze 50 leków. Katalog pochodzi z migracji (mock); brak zapisu.

## Recepty

| Metoda | Ścieżka | Uprawnienie | Parametry / body | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| GET | `/prescriptions` | `prescription:read` | query: `patientId`, `prescriberId`, `status`, `kind`, `page`, `size`, `sort` | Pag. `PrescriptionResponse` | 200; 422 |
| GET | `/prescriptions/{prescriptionId}` | `prescription:read` | - | `PrescriptionResponse` | 200; 404 |
| GET | `/patients/{patientId}/active-medications` | `prescription:read` | - | `ActiveMedicationResponse[]` | 200; 404 pacjent |
| POST | `/patients/{patientId}/prescriptions` | `prescription:create` | `PrescriptionCreateRequest` | `PrescriptionResponse` + `Location: /api/v1/prescriptions/{id}` | 201; 404 pacjent; 422 |
| POST | `/prescriptions/{prescriptionId}/cancel` | `prescription:cancel` | opcjonalne body `PrescriptionCancelRequest` (`reason?`, `version?`) | `PrescriptionResponse` | 200; 404; 409 |

`sort`: `issuedAt`, `validFrom`, `validUntil`, `status`, `kind`, `createdAt`; domyślnie `issuedAt` malejąco.

### Status efektywny

`status` w odpowiedzi jest **efektywny**: zapisana recepta `issued` lub `partially_dispensed` z `validUntil` wcześniejszym niż dziś (UTC) jest zwracana jako `expired` (nic nie jest zapisywane). Filtr `status` działa na statusie efektywnym: `expired` obejmuje także takie "żywe" recepty po terminie, a `issued`/`partially_dispensed` tylko te, których termin nie minął. Zapisane `dispensed`, `cancelled`, `expired` są końcowe. REST `/api/v1` nie ma endpointu realizacji: `partially_dispensed`/`dispensed`/`expired` ustawia e-receipt przez `PUT /fhir/MedicationRequest/{id}` ([rest-api-fhir.md](rest-api-fhir.md)) albo pochodzą z mocków.

### Wystawienie

- Aktor (`prescriberId`) z tokenu; status początkowy `issued`; `issuedAt` = teraz.
- `accessCode` = 4 losowe cyfry (`SecureRandom`, niewymagana unikalność); `eRxKey` = **lokalny** losowy klucz 44 znaków `A-Z0-9`. Dla `kind = e_prescription`, gdy integracja z e-receipt jest włączona, po commicie klucz jest podmieniany kluczem z e-receipt (bez zmiany `version`; odpowiedź `201` ma jeszcze klucz lokalny, nowy widać przy kolejnym odczycie). `hospital_order` i recepty z niedostępnym e-receipt zachowują klucz lokalny ([rest-api-fhir.md](rest-api-fhir.md)).
- Pozycje (`items[]`, min. 1): `drugId` musi istnieć (422 `items[i].drugId`/`notFound`); `dosage.route` musi być jedną z dróg podania leku (`items[i].dosage.route`/`notAllowed`); `reimbursement` musi być wśród opcji refundacji leku (`items[i].reimbursement`/`notAllowed`).
- Snapshot z katalogu: `drugName`, `activeSubstance`, `strength`, `form` (z żądania ignorowane).
- 422 także: `validUntil` przed `validFrom` (`beforeValidFrom`), `encounterId` nie należy do pacjenta, `patientId` niezgodny ze ścieżką.
- `dosage`: `dose` > 0 (do 3 miejsc po przecinku), `doseUnit` do 30 znaków, `durationDays` > 0, `maxPerDay` > 0 gdy podane.
- Pozycje w odpowiedzi sortowane po `drugName` rosnąco (potem `id`), a nie w kolejności żądania.
- Ostrzeżenia bezpieczeństwa są **doradcze** - nie blokują wystawienia. Zdarzenie: `PrescriptionIssued`.

### Anulowanie

Dozwolone dla statusu efektywnego `issued`/`partially_dispensed`; `dispensed`, `cancelled`, `expired` (także wygasła wg terminu) lub niezgodne `version` -> 409. Ciało opcjonalne. Ustawia `cancelledAt`, `cancelReason` (puste -> `null`). Zdarzenie: `PrescriptionCancelled`; dla `e_prescription` stan jest przekazywany do e-receipt (best effort).

### Aktywne leki

`GET /patients/{id}/active-medications`: pozycje recept `issued`/`partially_dispensed` z `validUntil` >= dziś (UTC), wg `validFrom` malejąco; `date` = `validFrom` recepty. Te same dane trafiają do `ehr-summary.activeMedications`.

## Kontrola bezpieczeństwa leku

| Metoda | Ścieżka | Uprawnienie | Body | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- | --- |
| POST | `/drug-safety-checks` | `drug-safety-check:run` (**tylko doctor**) | `DrugSafetyCheckRequest` | `DrugSafetyWarningResponse[]` (pusta = brak ostrzeżeń) | **200** (odczyt wyliczany, nie 201); 404 pacjent lub lek; 422 brak `drugId` i `items` |

Wejście: `patientId` (wymagane) oraz `drugId` (+ `dosage?`) i/lub `items[]` (`drugId`, `dosage?`); podane razem tworzą jedną listę (`drugId` jako pierwsza pozycja). `dosage.doseUnit` jest wymagane, jeśli `dosage` podano.

Dla każdej pozycji (w kolejności wejścia), w kolejności typów:

| Typ | `severity` | Reguła |
| --- | --- | --- |
| `allergy` | `danger` dla alergii `life_threatening`/`severe`, `warn` dla `mild`/`moderate` | aktywna alergia pacjenta (`status=active`): kod ATC alergii jest prefiksem ATC leku **albo** substancja alergii (bez wielkości liter/diakrytyków) równa substancji czynnej |
| `duplicate` | `warn` | ta sama substancja czynna w aktywnych lekach pacjenta (jedno ostrzeżenie na pozycję) albo we wcześniejszej pozycji tej samej recepty (także ten sam lek) |
| `interaction` | `warn` | `interactsWithAtc` (prefiks ATC) jednego leku pasuje do ATC drugiego, w obie strony; względem aktywnych leków pacjenta i wcześniejszych pozycji |
| `max_dose` | `danger` | dawka dobowa pozycji przekracza `maxDailyDose` leku; tylko gdy ta sama jednostka (po złożeniu diakrytyków, bez przeliczeń) i podano `dosage`; bez sumowania pozycji |

Dawka dobowa = `dose` x liczba podań z `frequency`: `QD`=1, `BID`/`Q12H`=2, `TID`/`Q8H`=3, `QID`/`Q6H`=4, `Q4H`=6, `QW`=1/7, `PRN`=1; dla `asNeeded` lub `PRN` z `maxPerDay`: `dose` x `maxPerDay`.

`DrugSafetyWarningResponse.drugId` to sprawdzany lek (pozycja), którego dotyczy ostrzeżenie; drugi lek (interakcja/duplikat) jest wymieniony w `message` (po polsku).
