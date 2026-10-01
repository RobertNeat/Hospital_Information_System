# Zdarzenia domenowe

Zdarzenia publikowane przez `his_backend` (rekordy w `*/events`), ich konsumenci i zamiana zdarzenie -> alert -> push. Powrót: [README.md](README.md). Push STOMP: [realtime-stomp.md](realtime-stomp.md).

## Mechanizm

- Zdarzenia to Java `record` publikowane przez `ApplicationEventPublisher` **w transakcji zapisu** (to wewnętrzne zdarzenia JVM; nie są wystawiane na zewnątrz poza pushem STOMP).
- `AlertEventListener` (`@EventListener`, **synchroniczny**) tworzy alerty w transakcji publikującego (`AlertService.raise` ma `Propagation.MANDATORY`). Skutek: błąd utworzenia alertu **wycofuje także zapis źródłowy** (np. zapis odczytu parametrów życiowych).
- `RealtimePublisher` (`@TransactionalEventListener(AFTER_COMMIT)`) wysyła push dopiero po commicie; rollback niczego nie wysyła, błąd pushu jest tylko logowany. Projekcje dla pushu (`MessagingPushProjection`) budowane w osobnej transakcji `REQUIRES_NEW`.
- `PresenceEventListener` konsumuje zdarzenia sesji STOMP (`SessionConnectedEvent`/`SessionDisconnectEvent`), nie zdarzenia domenowe.
- Komentarze w rekordach `*/events` wskazują konsumenta zgodnie z rejestrem poniżej.

## Rejestr zdarzeń

Kolumna "Publikuje" wskazuje miejsce w kodzie; "Konsument" - faktyczne listenery.

| Zdarzenie (pakiet) | Pola | Publikuje | Konsument |
| --- | --- | --- | --- |
| `PatientAdmitted` (`patient`) | `patientId`, `admissionId`, `encounterId`, `wardId`, `attendingPhysicianId`, `admissionType`, `admittedAt`, `actorId` | `PatientService.admit` | brak |
| `PatientDischarged` (`patient`) | `patientId`, `admissionId`, `encounterId`, `wardId`, `dischargedAt`, `disposition`, `dischargeSummaryNoteId`, `actorId` | `PatientService.discharge` | brak |
| `ClinicalNoteCreated` (`ehr`) | `noteId`, `patientId`, `encounterId`, `authorId`, `category`, `createdAt` | `EhrService.addNote` | brak |
| `DiagnosisRecorded` (`ehr`) | `diagnosisId`, `patientId`, `encounterId`, `code` (`Coding`), `type`, `diagnosedAt`, `actorId` | `EhrService.addDiagnosis` | brak |
| `AllergyRecorded` (`ehr`) | `allergyId`, `patientId`, `substance`, `severity`, `atcCodes`, `recordedAt`, `actorId` | `EhrService.addAllergy` | brak |
| `LabOrderStatusChanged` (`lab`) | `orderId`, `patientId`, `orderedById`, `previousStatus`, `status`, `at`, `actorId` (null = system), `note` | `LabOrderService.transition` (`/status`, `/cancel`); `LabResultRecordingService` (auto-`completed`) | `AlertEventListener` |
| `LabResultRecorded` (`lab`) | `resultId`, `patientId`, `orderId`, `orderedById`, `testCode`, `status`, `critical`, `criticalAnalyteCodes`, `recordedAt`, `actorId` | `LabResultRecordingService.recordResult` (bez endpointu HTTP) | `AlertEventListener` |
| `ImagingOrderStatusChanged` (`imaging`) | jak lab (`orderId`, `patientId`, `orderedById`, `previousStatus`, `status`, `at`, `actorId`, `note`) | `ImagingOrderService.transition`; `ImagingResultRecordingService` (auto-`completed`) | `AlertEventListener` |
| `ImagingResultRecorded` (`imaging`) | `resultId`, `patientId`, `orderId`, `orderedById`, `modality`, `status`, `critical`, `recordedAt`, `actorId` | `ImagingResultRecordingService.recordResult` (bez endpointu HTTP) | `AlertEventListener` |
| `PrescriptionIssued` (`prescription`) | `prescriptionId`, `patientId`, `prescriberId`, `kind`, `validFrom`, `validUntil`, `itemCount`, `issuedAt` | `PrescriptionService.issue` | brak |
| `PrescriptionCancelled` (`prescription`) | `prescriptionId`, `patientId`, `prescriberId`, `actorId`, `reason`, `cancelledAt` | `PrescriptionService.cancel` | brak |
| `VitalAnomalyDetected` (`vitals`) | `patientId`, `vitalsId`, `anomalies` (tylko `critical`), `recordedAt`, `actorId` | `VitalsService.record` - tylko gdy jest anomalia `critical` | `AlertEventListener` |
| `MessageSent` (`messaging`) | `messageId`, `threadId`, `threadSubject`, `patientId`, `senderId`, `priority`, `sentAt`, `recipientIds` (uczestnicy poza nadawcą) | `MessageThreadService` (utworzenie wątku i wysyłka) | `RealtimePublisher` |
| `ThreadMarkedRead` (`messaging`) | `staffId`, `thread` (`MessageThreadResponse`, `unreadCount=0`) | `MessageThreadService.markRead` | `RealtimePublisher` |
| `TaskAssigned` (`messaging`) | `taskId`, `assignedToId`, `createdById`, `patientId`, `title`, `priority`, `dueAt`, `at` | `TeamTaskService.create` | `AlertEventListener`, `RealtimePublisher` |
| `TaskStatusChanged` (`messaging`) | `taskId`, `assignedToId`, `createdById`, `previousStatus`, `status`, `actorId`, `at` | `TeamTaskService.updateStatus` | `RealtimePublisher` |
| `AlertCreated` (`alert`) | `alertId`, `type`, `severity`, `patientId`, `message`, `createdAt`, `target`, `wardId`, `recipientIds` | `AlertService.raise` | `RealtimePublisher` |
| `AlertAcknowledged` (`alert`) | `staffId`, `alert` (`AlertResponse` widza) | `AlertService.acknowledge` | `RealtimePublisher` |

Zdarzenia bez konsumenta (`PatientAdmitted`, `PatientDischarged`, `ClinicalNoteCreated`, `DiagnosisRecorded`, `AllergyRecorded`, `PrescriptionIssued`, `PrescriptionCancelled`) są publikowane, brak konsumenta (punkty rozszerzeń, np. integracja e-receipt).

## Zdarzenie -> alert (`AlertEventListener`)

Alert nie ma adresata w modelu (widzą go wszyscy z `alert:read`); `recipientIds` i `wardId` trafiają tylko do zdarzenia `AlertCreated` i pushu. Adresaci nie wykluczają aktora (zlecający, który sam kończy zlecenie, też dostaje alert/push).

| Zdarzenie | Warunek | `type` | `severity` | `target.kind` (`id`) | Adresaci (`recipientIds`) | Komunikat (po polsku) |
| --- | --- | --- | --- | --- | --- | --- |
| `LabResultRecorded` | `critical = true` (któraś obserwacja `LL`/`HH`) | `critical_result` | `critical` | `lab_result` (`resultId`) | zlecający (`orderedById`, jeśli jest) + lekarz prowadzący z aktywnego przyjęcia | "Krytyczny wynik badania laboratoryjnego <kod> (<anality>) - pacjent Imię Nazwisko." |
| `ImagingResultRecorded` | `critical = true` | `critical_result` | `critical` | `imaging_result` (`resultId`) | zlecający + lekarz prowadzący | "Krytyczny wynik badania obrazowego (<modalność>) - pacjent ..." |
| `VitalAnomalyDetected` | zawsze (publikowane tylko dla `critical`) | `vital_anomaly` | `critical` | `patient_vitals` (`patientId`) | lekarz prowadzący (brak zlecającego) | "Krytyczne parametry życiowe - pacjent ... <komunikaty anomalii>" |
| `LabOrderStatusChanged` | `status` = `completed` lub `cancelled` | `order_status` | `info` | `lab_order` (`orderId`) | zlecający | "Zakończono/Anulowano zlecenie laboratoryjne - pacjent ...[ Powód: <note>]" |
| `ImagingOrderStatusChanged` | `status` = `completed` lub `cancelled` | `order_status` | `info` | `imaging_order` (`orderId`) | zlecający | "Zakończono/Anulowano zlecenie obrazowe - pacjent ..." |
| `TaskAssigned` | zawsze | `task` | `info` dla `priority=normal`, w pozostałych `warning` | `task` (`taskId`) | osoba przypisana | "Nowe zadanie: <tytuł>[ (pacjent ...)]." |

- Zmiany statusu zlecenia inne niż `completed`/`cancelled` (np. `scheduled`, `in_progress`) alertu nie tworzą. Auto-`completed` po wyniku też tworzy alert `order_status`.
- Brak alertów dla: przyjęcia/wypisu, notatek, diagnoz, alergii, recept, wiadomości, zmiany statusu zadania (poza pushem `/user/queue/tasks`), anomalii o nasileniu `warning`.
- `AlertType.system` i `AlertTargetKind.patient` nie są przez nic tworzone.
- `critical_result` dla wyników laboratoryjnych/obrazowych powstaje tylko przez wewnętrzne serwisy zapisu wyników (brak endpointu HTTP), a w danych demo - przez migracje `mock`.

## Zdarzenie -> push STOMP (`RealtimePublisher`)

| Zdarzenie | Temat | Adresaci |
| --- | --- | --- |
| `AlertCreated` | `/user/queue/alerts`, `/topic/alerts/{wardId}` | `recipientIds`; subskrybenci oddziału |
| `AlertAcknowledged` | `/user/queue/alerts` | potwierdzający |
| `MessageSent` | `/user/queue/messages`, `/user/queue/threads` | odbiorcy bez nadawcy; wszyscy uczestnicy |
| `ThreadMarkedRead` | `/user/queue/threads` | użytkownik |
| `TaskAssigned`, `TaskStatusChanged` | `/user/queue/tasks` | osoba przypisana |

Szczegóły payloadów i reguł subskrypcji: [realtime-stomp.md](realtime-stomp.md).
