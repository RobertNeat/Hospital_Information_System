# FHIR: integracja z usługami e-* (recepty / e-receipt, badania laboratoryjne / e-laboratory, badania obrazowe / e-imaging)

Endpointy FHIR R4 wystawiane przez `his_backend` dla usług `e-*` oraz klienci FHIR wywołujący `e-receipt`, `e-laboratory` i `e-imaging`. Powrót: [README.md](README.md). Konfiguracja: [deployment-and-config.md](deployment-and-config.md). Zdarzenia: [events.md](events.md).

Biblioteka: HAPI FHIR R4 (`hapi-fhir-base` + `hapi-fhir-structures-r4`, wersja z `hapi-fhir.version` w `pom.xml`); `FhirContext` to singleton (`common/fhir/FhirConfig`). Treść żądań i odpowiedzi FHIR jest `String` serializowanym przez HAPI (Jackson nie obsługuje `application/fhir+json`); klient HTTP to Spring `RestClient`.

## Model przepływu (recepty)

```
HIS: wystawienie recepty e_prescription
  -> (AFTER_COMMIT) POST  {e-receipt}/fhir/MedicationRequest      -> eRxKey (zapis w HIS)
e-receipt (UI): zmiana stanu (dispensed, partially_dispensed, cancelled, expired)
  -> PUT {his-backend}/fhir/MedicationRequest/{id recepty HIS}    -> stan recepty w HIS
HIS: anulowanie recepty przez lekarza
  -> (AFTER_COMMIT) PUT  {e-receipt}/fhir/MedicationRequest/{eRxKey}
```

Do e-receipt trafiają tylko recepty `kind = e_prescription`; `hospital_order` zostają wewnętrzne (zachowują lokalny `eRxKey`).

## Endpointy wystawione przez his_backend

Ścieżki **poza** `/api/v1` (osobny łańcuch bezpieczeństwa). Kontroler: `prescription/ereceipt/PrescriptionFhirController`. `Content-Type` żądania: `application/fhir+json` lub `application/json`.

| Metoda | Ścieżka | Ciało | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- |
| GET | `/fhir/MedicationRequest/{id}` | - | `MedicationRequest` (status efektywny) | 200; 401; 404 |
| PUT | `/fhir/MedicationRequest/{id}` | `MedicationRequest` (status + opcjonalne identyfikatory) | `MedicationRequest` po zmianie | 200; 400; 401; 404; 409; 422 |

`{id}` to identyfikator recepty w HIS (UUID, ten sam co `PrescriptionResponse.id`); zły format = 404. Błędy to `OperationOutcome` (`application/fhir+json`), a nie `ProblemDetail`.

### Autoryzacja (klucz usługowy)

Nagłówek `X-Service-Key` musi być równy `his.fhir.service-key` (`HIS_FHIR_SERVICE_KEY`); porównanie w stałym czasie. Pusty klucz w konfiguracji = każde żądanie `/fhir/**` to 401 (`OperationOutcome`, `issue.code=security`). Token JWT nie autoryzuje `/fhir/**`, a klucz usługowy nie działa na `/api/**`. To rozwiązanie przejściowe: docelowo mTLS (profil `mtls`, paczka SSL `fhir-client` po stronie klienta); klucz wtedy zostaje usunięty.

### Zmiana stanu (PUT)

Docelowy stan: rozszerzenie `urn:his:fhir:prescription-status` (`valueString` = status HIS: `issued`, `partially_dispensed`, `dispensed`, `cancelled`, `expired`); bez rozszerzenia z `status` R4:

| `status` R4 | Status HIS |
| --- | --- |
| `active` | `issued` (`partially_dispensed` tylko przez rozszerzenie) |
| `completed` | `dispensed` |
| `cancelled` | `cancelled` |
| `stopped` | `expired` |
| inne (`draft`, `on-hold`, ...) | 400 |

Reguły (`PrescriptionExternalService`):

- Identyfikator `urn:his:erx-key` w ciele, jeśli podany, musi równać się `eRxKey` recepty (422).
- Stan równy zapisanemu: 200 bez zmiany (idempotencja; bez nowej wersji i zdarzenia).
- Żądanie `issued` (inne niż zapisany stan): 422.
- Zapisany stan nie jest "żywy" (`dispensed`, `cancelled`, `expired`): 409.
- Recepta po terminie (`validUntil` < dziś UTC, zapisana jako `issued`/`partially_dispensed`) przyjmuje tylko `expired` (zapisywane); inne stany: 409. Wygasanie nadal jest też wyliczane przy odczycie REST.
- `cancelled`: ustawia `cancelledAt` i `cancelReason = "Anulowano w e-receipt"`, publikuje `PrescriptionCancelled` z `actorId = null`.
- Zapis zwiększa `version` (optymistyczne blokowanie), audyt `updatedBy` = null (brak użytkownika HIS). Frontend widzi nowy status po ponownym pobraniu; STOMP nie wysyła pushu dla recept.
- Realizacji (`dispensed`/`partially_dispensed`) nie ma w REST `/api/v1`; zmienia ją wyłącznie ten endpoint (anulowanie ma też `POST /prescriptions/{id}/cancel`).

## Zasób MedicationRequest (wysyłany do e-receipt)

`prescription/ereceipt/PrescriptionFhirMapper`. Bez danych osobowych pacjenta (tylko referencje).

| Element | Zawartość |
| --- | --- |
| `id` | `eRxKey` recepty (lokalny w chwili wysyłki; e-receipt nadaje własny) |
| `identifier` | `urn:his:prescription-id` (UUID HIS), `urn:his:erx-key`, `urn:his:access-code` |
| `status`, `intent` | `active` (przy wystawieniu), `order`; rozszerzenie `urn:his:fhir:prescription-status` z kodem HIS |
| `subject`, `requester` | `Patient/{patientId}`, `Practitioner/{prescriberId}` |
| `authoredOn` | `issuedAt` (UTC) |
| `medicationCodeableConcept.text` | `nazwa moc` pozycji, połączone `; ` |
| `dosageInstruction[].text` | `nazwa: dawka jednostka częstość droga, N dni[, doraźnie][ (instrukcja)], opak.: N` |
| `dispenseRequest.validityPeriod` | `validFrom` / `validUntil` (daty) |
| `note[0].text` | `notes` |

## Klient e-receipt

`prescription/ereceipt/EReceiptIntegration` (listener `@TransactionalEventListener(AFTER_COMMIT)`) + `EReceiptClient`.

- **Domyślnie wyłączony** (`his.integration.ereceipt.enabled=false`). Brak e-receipt (timeout, połączenie, HTTP, zły format odpowiedzi) jest tylko logowany; wystawienie i anulowanie recepty nigdy od niego nie zależy. Wywołanie jest synchroniczne (limity: `connect-timeout` 1 s, `read-timeout` 3 s), poza transakcją bazodanową, więc niedostępny e-receipt może wydłużyć odpowiedź `POST /prescriptions` o czas limitów.
- `PrescriptionIssued` (`e_prescription`): `POST {base-url}/MedicationRequest`; z odpowiedzi brany `eRxKey` (`urn:his:erx-key`, a w razie braku `id`), wymagany format `[A-Z0-9]{44}` (kolumna `char(44)`). Zapis w osobnej transakcji (`REQUIRES_NEW`) zbiorczym JPQL `PrescriptionRepository.updateERxKey` **bez zmiany `version`**, żeby `version` zwrócone przy wystawieniu pozostało aktualne dla `cancel`. Odpowiedź `201` wystawienia niesie jeszcze klucz lokalny; klucz z e-receipt jest widoczny przy kolejnym odczycie.
- `PrescriptionCancelled` z aktorem (anulowanie w HIS): `PUT {base-url}/MedicationRequest/{eRxKey}` ze statusem `cancelled`. 404 z e-receipt (recepta mock/lokalny klucz) = log informacyjny. Zdarzenie bez aktora (anulowanie pochodzące z e-receipt) nie jest odsyłane (brak pętli).
- Bez ponawiania i bez kolejki: nieudana wysyłka nie jest powtarzana (stan HIS pozostaje autorytatywny, recepta bez klucza e-receipt zachowuje klucz lokalny).
- mTLS: `his.integration.ereceipt.ssl-bundle` = nazwa paczki `spring.ssl.bundle.*` (np. `fhir-client` z profilu `mtls`); `RestClient` budowany przez `ClientHttpRequestFactoryBuilder` z `HttpClientSettings.withSslBundle`.

## Badania laboratoryjne (e-laboratory)

Pakiet `lab/elab` (`ELabProperties`, `ELabClient`, `ELabIntegration`, `LabFhirMapper`, `LabOrderExternalService`, `LabResultExternalService`, `LabFhirHandler`). Błędy to `OperationOutcome`, autoryzacja i `Content-Type` jak dla recept. Ścieżki `/fhir/ServiceRequest` i `/fhir/DiagnosticReport` są wspólne z badaniami obrazowymi (patrz [Wspólne ścieżki](#wspólne-ścieżki-servicerequest-i-diagnosticreport)).

```
HIS: utworzenie zlecenia laboratoryjnego
  -> (AFTER_COMMIT) POST {e-laboratory}/fhir/ServiceRequest              (id = id zlecenia HIS)
e-laboratory (UI): zmiana stanu (scheduled, specimen_collected, in_progress, completed, cancelled)
  -> PUT  {his-backend}/fhir/ServiceRequest/{id zlecenia HIS}            -> stan zlecenia w HIS
e-laboratory (UI): wynik badania z pozycji zlecenia
  -> POST {his-backend}/fhir/DiagnosticReport                             -> LabResultRecordingService.recordResult
HIS: anulowanie zlecenia przez lekarza
  -> (AFTER_COMMIT) PUT  {e-laboratory}/fhir/ServiceRequest/{id zlecenia HIS}
```

Identyfikatorem zlecenia po obu stronach jest `id` zlecenia HIS (UUID; e-laboratory nie nadaje własnego klucza, HIS niczego nie zapisuje po wysyłce).

### Endpointy wystawione przez his_backend

| Metoda | Ścieżka | Ciało | Odpowiedź | Statusy |
| --- | --- | --- | --- | --- |
| GET | `/fhir/ServiceRequest/{id}` | - | `ServiceRequest` | 200; 401; 404 |
| PUT | `/fhir/ServiceRequest/{id}` | `ServiceRequest` (status) | `ServiceRequest` po zmianie | 200; 400; 401; 404; 409; 422 |
| POST | `/fhir/DiagnosticReport` | `DiagnosticReport` z obserwacjami `contained` | zapisany `DiagnosticReport` (`id` = id wyniku HIS, `Location`) | 201; 200 (powtórzony raport); 400; 401; 409; 422 |
| GET | `/fhir/DiagnosticReport/{id}` | - | `DiagnosticReport` | 200; 401; 404 |

### Zmiana stanu zlecenia (PUT)

Docelowy stan: rozszerzenie `urn:his:fhir:lab-order-status` (`valueString` = status HIS: `ordered`, `scheduled`, `specimen_collected`, `in_progress`, `completed`, `cancelled`); bez rozszerzenia ze `status` R4: `active` -> `ordered`, `completed` -> `completed`, `revoked` -> `cancelled`, inne -> 400. Reguły (`LabOrderExternalService`), zgodne z istniejącą maszyną stanów (`LabOrderStateMachine`):

- Identyfikator `urn:his:lab-order-id` w ciele, jeśli podany, musi równać się `id` zlecenia (422).
- Stan równy zapisanemu: 200 bez zmiany (idempotencja; bez nowej wersji i zdarzenia).
- Żądanie `ordered` (inne niż zapisany stan): 422.
- Zlecenie w stanie końcowym (`completed`, `cancelled`) lub przejście niedozwolone przez maszynę stanów (np. `scheduled -> in_progress`): 409.
- Zmiana publikuje `LabOrderStatusChanged` z `actorId = null` (wpis historii bez autora, notatka "Zmiana stanu w e-laboratory" albo "Anulowano w e-laboratory"), zwiększa `version`, audyt `updatedBy` = null; `completed` i `cancelled` tworzą alert `order_status`.
- Zmiany stanu wykonane w HIS (poza anulowaniem) nie są przekazywane do e-laboratory; ewentualna rozbieżność (np. HIS ma już `in_progress`, a e-laboratory zgłasza `specimen_collected`) kończy się 409 widocznym w UI e-laboratory.

### Wynik (POST DiagnosticReport)

Mapowanie na `LabResultRecordingService.recordResult` (`LabResultExternalService`, aktor systemowy `null`); wszystkie reguły zapisu wyniku obowiązują bez zmian ([rest-api-lab.md](rest-api-lab.md#zapis-wyniku-poza-rest)): walidacja pól i analitów 422, stan zlecenia i wynik ostateczny 409, flagi, zdarzenie `LabResultRecorded`, auto-`completed`.

| Element `DiagnosticReport` | Zawartość |
| --- | --- |
| `status` | `preliminary`, `final`, `corrected` (inne -> 400) |
| `code.coding` | `urn:his:lab-test` = kod badania z katalogu |
| `subject` | `Patient/{uuid}` (musi zgadzać się ze zleceniem, 422) |
| `basedOn[0]` | `ServiceRequest/{uuid zlecenia HIS}` (wymagane; brak -> 400) |
| `effectiveDateTime`, `issued` | `collectedAt`, `resultedAt` (wymagane) |
| `performer[0].display` | `performerName` (domyślnie "e-laboratory") |
| `conclusion` | komentarz |
| `result` + `contained` | `Observation` lokalne (`#id`): `code.coding` `urn:his:lab-analyte` = kod analitu; `valueQuantity.value` (liczba) albo `valueString`; `interpretation` (HL7 `v3-ObservationInterpretation`: `N`, `L`, `H`, `LL`, `HH`, `A`) = jawna flaga (brak = z zakresu katalogu); `valueQuantity.unit` i `referenceRange` są informacyjne - HIS zapisuje jednostkę i zakres z katalogu (snapshot) |

Idempotencja: raport pozycji zlecenia o tym samym statusie i czasie `issued` co zapisany wynik zwraca ten wynik (200) zamiast konfliktu 409 (HIS nie zapisuje identyfikatora wyniku e-laboratory). Odpowiedź `GET`/`POST` ma `id` wyniku HIS i obserwacje jako `contained`.

### Zasób ServiceRequest (wysyłany do e-laboratory)

`LabFhirMapper`. Bez danych osobowych pacjenta (tylko referencje).

| Element | Zawartość |
| --- | --- |
| `id`, `identifier` | id zlecenia HIS; `urn:his:lab-order-id` |
| `status`, `intent` | status R4 (`ordered`/`scheduled`/`specimen_collected`/`in_progress` -> `active`, `completed`, `cancelled` -> `revoked`), `order`; rozszerzenie `urn:his:fhir:lab-order-status` z kodem HIS |
| `priority` | `routine`, `urgent`, `stat` |
| `subject`, `requester` | `Patient/{patientId}`, `Practitioner/{orderedById}` |
| `authoredOn`, `occurrenceDateTime` | `orderedAt`, `plannedCollectionAt` (UTC) |
| `code.text` | kody badań połączone `; ` |
| `orderDetail[]` | po jednym na pozycję: `coding` `urn:his:lab-test` (kod, nazwa) i `urn:his:specimen-type` (materiał); rozszerzenia `urn:his:fhir:lab-analyte` (podrozszerzenia `code`, `name`, `unit`, `low`, `high`) z definicjami analitów badania z katalogu |
| `patientInstruction` | "Na czczo" (gdy `fasting`) |
| `reasonCode` | diagnoza (`diagnosisCode`), jeśli jest |
| `note` | `clinicalInfo`, `notes` |

### Klient e-laboratory

`lab/elab/ELabIntegration` (listener `@TransactionalEventListener(AFTER_COMMIT)`) + `ELabClient`.

- **Domyślnie wyłączony** (`his.integration.elab.enabled=false`). Brak e-laboratory (timeout, połączenie, HTTP) jest tylko logowany; utworzenie i anulowanie zlecenia nigdy od niego nie zależy. Wywołanie jest synchroniczne (`connect-timeout` 1 s, `read-timeout` 3 s), poza transakcją bazodanową.
- `LabOrderPlaced` (publikowane przez `LabOrderService.create`): `POST {base-url}/ServiceRequest`.
- `LabOrderStatusChanged` na `cancelled` z aktorem (anulowanie w HIS): `PUT {base-url}/ServiceRequest/{id zlecenia}`. 404 z e-laboratory (zlecenie mock/sprzed integracji) = log informacyjny. Zdarzenie bez aktora (zmiana pochodząca z e-laboratory, także auto-`completed` po wyniku) nie jest odsyłane (brak pętli).
- Bez ponawiania i bez kolejki: zlecenie, które nie dotarło do e-laboratory, nie jest wysyłane ponownie (stan HIS pozostaje autorytatywny).
- mTLS: `his.integration.elab.ssl-bundle` jak dla e-receipt.

## Wspólne ścieżki ServiceRequest i DiagnosticReport

Badania laboratoryjne i obrazowe używają tych samych typów zasobów, więc `common/fhir/ServiceRequestFhirController` i `DiagnosticReportFhirController` kierują żądanie do modułu przez interfejs `FhirOrderHandler` (implementacje: `lab/elab/LabFhirHandler`, `imaging/eimg/ImagingFhirHandler`):

- `GET`/`PUT /fhir/ServiceRequest/{id}`: moduł, w którym istnieje zlecenie o tym `id` (UUID zleceń laboratoryjnych i obrazowych nie powtarzają się); nieznane lub nieprawidłowe `id` to 404.
- `POST /fhir/DiagnosticReport`: moduł wskazuje system kodu badania w `code.coding` (`urn:his:lab-test` albo `urn:his:imaging-exam`); żaden z nich -> 400.
- `GET /fhir/DiagnosticReport/{id}`: wynik z dowolnego modułu; nieznany -> 404.
- Niepoprawny JSON `ServiceRequest`/`DiagnosticReport` -> 400 (kontroler wspólny, przed routingiem).

## Badania obrazowe (e-imaging)

Pakiet `imaging/eimg` (`EImgProperties`, `EImgClient`, `EImgIntegration`, `ImagingFhirMapper`, `ImagingOrderExternalService`, `ImagingResultExternalService`, `ImagingFhirHandler`). Błędy, autoryzacja i `Content-Type` jak dla pozostałych usług; id zlecenia HIS jest identyfikatorem po obu stronach.

```
HIS: utworzenie zlecenia obrazowego (ze slotem albo bez)
  -> (AFTER_COMMIT) POST {e-imaging}/fhir/ServiceRequest                  (id = id zlecenia HIS)
e-imaging (UI): zmiana stanu (ordered, scheduled, in_progress, completed, cancelled)
  -> PUT  {his-backend}/fhir/ServiceRequest/{id zlecenia HIS}            -> stan zlecenia w HIS
e-imaging (UI): wynik badania (opis, wnioski, krytyczny)
  -> POST {his-backend}/fhir/DiagnosticReport                             -> ImagingResultRecordingService.recordResult
HIS: anulowanie zlecenia przez lekarza
  -> (AFTER_COMMIT) PUT  {e-imaging}/fhir/ServiceRequest/{id zlecenia HIS}
```

### Zmiana stanu zlecenia (PUT)

Docelowy stan: rozszerzenie `urn:his:fhir:imaging-order-status` (`valueString` = status HIS: `ordered`, `scheduled`, `in_progress`, `completed`, `cancelled`); bez rozszerzenia ze `status` R4: `active` -> `ordered`, `completed` -> `completed`, `revoked` -> `cancelled`, inne -> 400. Reguły (`ImagingOrderExternalService`), zgodne z `ImagingOrderStateMachine`:

- Identyfikator `urn:his:imaging-order-id` w ciele, jeśli podany, musi równać się `id` zlecenia (422).
- Stan równy zapisanemu: 200 bez zmiany (idempotencja).
- Żądanie `ordered` (inne niż zapisany stan) i `specimen_collected`: 422.
- Zlecenie w stanie końcowym (`completed`, `cancelled`) lub przejście niedozwolone przez maszynę stanów (np. `in_progress -> scheduled`): 409.
- Zmiana publikuje `ImagingOrderStatusChanged` z `actorId = null` (wpis historii bez autora, notatka "Zmiana stanu w e-imaging" albo "Anulowano w e-imaging"), zwiększa `version`, audyt `updatedBy` = null; `completed` i `cancelled` tworzą alert `order_status`. Anulowanie zwalnia zarezerwowany slot (jak `/cancel`).
- Zmiany stanu wykonane w HIS (poza anulowaniem) nie są przekazywane do e-imaging; rozbieżność kończy się 409 widocznym w UI e-imaging.

### Wynik (POST DiagnosticReport)

Mapowanie na `ImagingResultRecordingService.recordResult` (`ImagingResultExternalService`, aktor systemowy `null`); wszystkie reguły zapisu obowiązują bez zmian ([rest-api-imaging.md](rest-api-imaging.md#zapis-wyniku-poza-rest)): walidacja pól 422, stan zlecenia (`scheduled`/`in_progress`) i wynik ostateczny 409, zdarzenie `ImagingResultRecorded`, auto-`completed` po wyniku `final`.

| Element `DiagnosticReport` | Zawartość |
| --- | --- |
| `status` | `preliminary`, `final` (inne, także `corrected` -> 400) |
| `code.coding` | `urn:his:imaging-exam` = kod badania zlecenia (wymagany do routingu; niezgodny ze zleceniem -> 422) |
| `subject` | `Patient/{uuid}` (musi zgadzać się ze zleceniem, 422) |
| `basedOn[0]` | `ServiceRequest/{uuid zlecenia HIS}` (wymagane; brak -> 400) |
| `effectiveDateTime`, `issued` | `performedAt` (wykonanie), `reportedAt` (opis; nie wcześniej niż wykonanie, 422) - wymagane (400) |
| `performer[0].display` | radiolog (domyślnie "e-imaging") |
| `conclusion` | wnioski (wymagane, 422) |
| rozszerzenie `urn:his:fhir:imaging-findings` | `valueString` = opis badania (wymagany, 422) |
| rozszerzenie `urn:his:fhir:imaging-critical` | `valueBoolean` = wynik krytyczny wg radiologa (domyślnie `false`) |

Idempotencja: raport zlecenia o tym samym statusie i czasie `issued` co zapisany wynik zwraca ten wynik (200) zamiast konfliktu; sprawdzana przed zapisem, bo po wyniku `final` zlecenie jest już `completed`. Odpowiedź `GET`/`POST` ma `id` wyniku HIS, `code.text` = nazwa badania, `code.coding` `urn:his:imaging-modality` i te same rozszerzenia.

### Zasób ServiceRequest (wysyłany do e-imaging)

`ImagingFhirMapper`. Bez danych osobowych pacjenta (tylko referencje) i bez listy kontrolnej bezpieczeństwa.

| Element | Zawartość |
| --- | --- |
| `id`, `identifier` | id zlecenia HIS; `urn:his:imaging-order-id` |
| `status`, `intent` | status R4 (`ordered`/`scheduled`/`in_progress` -> `active`, `completed`, `cancelled` -> `revoked`), `order`; rozszerzenie `urn:his:fhir:imaging-order-status` z kodem HIS (zlecenie ze slotem jest od razu `scheduled`) |
| `priority` | `routine`, `urgent`, `stat` |
| `subject`, `requester` | `Patient/{patientId}`, `Practitioner/{orderedById}` |
| `authoredOn`, `occurrenceDateTime` | `orderedAt`, termin badania (`scheduledAt` = początek slotu; brak bez slotu) |
| `code` | `coding` `urn:his:imaging-exam` (kod, nazwa), `text` = nazwa badania |
| `orderDetail[0]` | `coding` `urn:his:imaging-modality` (modalność) i `urn:his:imaging-laterality` (`left`, `right`, `bilateral`, `na`) |
| `bodySite[0].text` | okolica badania |
| rozszerzenia | `urn:his:fhir:imaging-contrast` (`valueBoolean`), `urn:his:fhir:imaging-slot` (`valueString` = id slotu, gdy zarezerwowany) |
| `reasonCode` | `text` = wskazanie kliniczne; drugi wpis z diagnozą (`diagnosisCode`), jeśli jest |
| `note` | pytanie kliniczne (`clinicalQuestion`), jeśli jest |

### Klient e-imaging

`imaging/eimg/EImgIntegration` (listener `@TransactionalEventListener(AFTER_COMMIT)`) + `EImgClient`; zachowanie jak klient e-laboratory:

- **Domyślnie wyłączony** (`his.integration.eimg.enabled=false`). Brak e-imaging (timeout, połączenie, HTTP) jest tylko logowany; utworzenie i anulowanie zlecenia nigdy od niego nie zależy. Wywołanie synchroniczne (`connect-timeout` 1 s, `read-timeout` 3 s), poza transakcją bazodanową.
- `ImagingOrderPlaced` (publikowane przez `ImagingOrderService.create`): `POST {base-url}/ServiceRequest`.
- `ImagingOrderStatusChanged` na `cancelled` z aktorem (anulowanie w HIS): `PUT {base-url}/ServiceRequest/{id zlecenia}`. 404 z e-imaging (zlecenie mock/sprzed integracji) = log informacyjny. Zdarzenie bez aktora (zmiana z e-imaging, także auto-`completed` po wyniku) nie jest odsyłane (brak pętli).
- Bez ponawiania i bez kolejki: zlecenie, które nie dotarło do e-imaging, nie jest wysyłane ponownie (stan HIS pozostaje autorytatywny).
- mTLS: `his.integration.eimg.ssl-bundle` jak dla pozostałych klientów.

## Wzorzec dla kolejnych usług

Wspólne elementy w `common/fhir` (`FhirConfig`, `FhirServiceProperties`, `FhirSecurityConfig`, `FhirEndpoint`, `FhirException`, `FhirExceptionHandler`, `FhirSystems`, `FhirOrderHandler` z kontrolerami `ServiceRequest`/`DiagnosticReport`); część domenowa w pakiecie modułu (`prescription/ereceipt`, `lab/elab`, `imaging/eimg`: properties, klient, listener, mapper, serwis zmiany stanu, handler lub kontroler). Nowy kontroler FHIR oznacza się `@FhirEndpoint` (błędy jako `OperationOutcome`), a ścieżka pod `/fhir/**` jest automatycznie chroniona kluczem usługowym.
