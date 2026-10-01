# FHIR: integracja z usługami e-* (recepty / e-receipt)

Endpointy FHIR R4 wystawiane przez `his_backend` dla usług `e-*` oraz klient FHIR wywołujący `e-receipt`. Powrót: [README.md](README.md). Konfiguracja: [deployment-and-config.md](deployment-and-config.md). Zdarzenia: [events.md](events.md).

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

## Wzorzec dla kolejnych usług (e-laboratory, e-imaging)

Wspólne elementy w `common/fhir` (`FhirConfig`, `FhirServiceProperties`, `FhirSecurityConfig`, `FhirEndpoint`, `FhirException`, `FhirExceptionHandler`, `FhirSystems`); część domenowa w pakiecie modułu (`prescription/ereceipt`: properties, klient, listener, mapper, serwis zmiany stanu, kontroler). Nowy kontroler FHIR oznacza się `@FhirEndpoint` (błędy jako `OperationOutcome`), a ścieżka pod `/fhir/**` jest automatycznie chroniona kluczem usługowym.
