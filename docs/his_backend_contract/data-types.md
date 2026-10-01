# Typy danych (DTO i enumy)

Wszystkie DTO żądań i odpowiedzi `his_backend` oraz enumy z wartościami na drucie. Powrót: [README.md](README.md). Konwencje (null, daty, błędy): [conventions.md](conventions.md).

Legenda: **Wym.** = `T` wymagane przez walidację żądania (`@NotNull`/`@NotBlank`/`@NotEmpty`), `N` opcjonalne. W odpowiedziach pola `null` są pomijane (NON_ABSENT), chyba że wskazano inaczej; "zawsze" = pole zawsze obecne. `ts` = nazwa typu w `apps/his_frontend/src/app/models` (gdy różni się od nazwy Java lub nie ma odpowiednika). Liczby dziesiętne (`BigDecimal`) są serializowane jako liczby JSON. `UUID`, `Instant`, `LocalDate`: [conventions.md](conventions.md).

Pola `version` (long) w odpowiedziach dotyczą optymistycznego blokowania ([conventions.md](conventions.md#optymistyczne-blokowanie-version)). Pola audytu (`createdAt`, `createdById`, `updatedAt`, `updatedById`) są nadawane przez backend.

## Wspólne

### `ProblemDetail` / `FieldError` (ts: `ProblemDetail`, `FieldError`)

| Pole | Typ | Uwagi |
| --- | --- | --- |
| `type`, `title`, `status`, `detail`, `instance` | string / int | RFC 9457 |
| `code` | `ApiErrorCode` | `NOT_FOUND`, `VALIDATION_FAILED`, `CONFLICT`, `FORBIDDEN`, `UNAUTHENTICATED`, `INTERNAL`; brak dla 405/415 i terminologii (poza 404) |
| `errors[]` | `{ field, message, code? }` | tylko 422 |

### `PageResponse<T>` (ts: `Page<T>`)

`items: T[]`, `page` (od 0), `size`, `totalElements` (long), `totalPages`.

### `Coding` (ts: `Coding`)

| Pole | Typ | Wym. | Ograniczenia |
| --- | --- | --- | --- |
| `system` | `CodingSystem` | T | |
| `code` | string | T | niepusty, do 30 |
| `display` | string | T | niepusty, do 500 |

### Zlecenia (wspólne lab/obrazowe)

| DTO | Pola | Uwagi |
| --- | --- | --- |
| `OrderStatusUpdateRequest` (ts: `OrderStatusUpdateRequest`) | `status` `OrderStatus` (T), `note` string (N), `version` long (N) | |
| `OrderCancelRequest` (ts: `OrderCancelRequest`) | `reason` string (T, niepusty), `version` long (N) | |
| `StatusChangeResponse` (ts: `StatusChange`) | `status`, `at`, `byId` (opc.), `note` (opc.) | historia rosnąco po `at` |
| `ResultAcknowledgeRequest` (ts: `ResultAcknowledgeRequest`) | `version` long (N) | **ignorowane** |

## Auth i kadry

| DTO (ts) | Pola |
| --- | --- |
| `LoginRequest` (`LoginRequest`) | `employeeId` string T (niepusty, <= 30), `password` string T (niepusty, <= 200) |
| `LoginResponse` (`LoginResponse`) | `accessToken` string, `user` `CurrentUserResponse`, `expiresAt` Instant (zawsze) |
| `CurrentUserResponse` (`CurrentUser`) | pola `StaffMemberResponse` (spłaszczone) + `permissions` string[] (bez `ROLE_*`) |
| `StaffRegistrationRequest` (`StaffRegistrationRequest`) | `firstName` T (<= 100), `lastName` T (<= 100), `role` `StaffRole` T, `title` T (<= 50), `specialization` N (<= 100), `pwz` N (pusty lub 7 cyfr), `wardId` UUID T (musi istnieć), `phone` N (<= 30), `email` N (e-mail, <= 200), `employeeId` T (`^[A-Za-z0-9-]{4,20}$`), `password` T (8-72 znaki, <= 72 bajty) |
| `StaffRegistrationResponse` (`StaffRegistrationResponse`) | `staffId` UUID, `accountStatus` (zawsze `pending`) |
| `StaffMemberResponse` (`StaffMember`) | `id`, `title`, `firstName`, `lastName`, `role` `StaffRole`, `specialization` (opc.), `wardId`, `phone` (opc.), `pwz` (opc.), `employeeId` (opc.), `email` (opc.), `accountStatus` `StaffAccountStatus` (opc.), `online` boolean (zawsze) |
| `WardResponse` (`Ward`) | `id`, `name`, `shortName`, `floor`, `beds` int |
| `DashboardStatsResponse` (`DashboardStats`) | `admittedPatients`, `newResults`, `criticalAlerts`, `openTasks`, `pendingOrders`, `vitalsAnomalies` (wszystkie long, zawsze) |

## Pacjent i przyjęcie

### Osadzone

| DTO (ts) | Pola | Ograniczenia |
| --- | --- | --- |
| `Address` (`Address`) | `street` T (<= 200), `buildingNumber` T (<= 20), `apartmentNumber` N (<= 20), `postalCode` T (<= 20), `city` T (<= 100), `country` T (<= 100) | |
| `EmergencyContact` (`EmergencyContact`) | `fullName` T (<= 200), `relation` T (<= 100), `phone` T (<= 30), `isLegalGuardian` boolean T | wszystkie pola albo cały obiekt pominięty |
| `IdentityDocument` (`IdentityDocument`) | `type` `IdentityDocumentType` T, `number` T (<= 50) | |
| `Insurance` (`Insurance`) | `status` `InsuranceStatus` T, `nfzBranch` T (<= 100), `payer` `InsurancePayer` T, `ewusVerifiedAt` Instant N | |

### `PatientCreateRequest` (ts: `PatientCreateRequest`; `PATCH` używa tych samych pól + `version`)

| Pole | Typ | Wym. | Ograniczenia |
| --- | --- | --- | --- |
| `pesel` | string | N | 11 cyfr; `null` wymaga `noPeselReason` |
| `noPeselReason` | `NoPeselReason` | warunkowo | wymagane, gdy brak `pesel` |
| `identityDocument` | `IdentityDocument` | N | |
| `firstName`, `lastName` | string | T | niepuste, <= 100 |
| `secondName` | string | N | <= 100 |
| `birthDate` | LocalDate | T | nie z przyszłości |
| `gender` | `Gender` | T | |
| `phone` | string | N | <= 30 |
| `email` | string | N | <= 200 |
| `address` | `Address` | T | |
| `emergencyContact` | `EmergencyContact` | N | |
| `insurance` | `Insurance` | T | |
| `bloodType` | `BloodType` | N | |
| `flags` | `PatientFlag[]` | N | |

`DuplicateCheckRequest`: `pesel` string T (11 cyfr).

### `PatientResponse` (ts: `Patient`)

`id`, `mrn`, `pesel` (**zawsze**, może być `null`), `noPeselReason`, `identityDocument`, `firstName`, `secondName`, `lastName`, `birthDate`, `gender`, `phone`, `email`, `address`, `emergencyContact`, `insurance`, `bloodType`, `status` `PatientStatus` (ts: `AdmissionStatus`), `currentAdmission` `AdmissionResponse` (projekcja aktywnego przyjęcia), `flags` (w kolejności enuma), `createdAt`, `updatedAt`, `createdById`, `updatedById`, `version`.

### `PatientSummaryResponse` (ts: `PatientSummary`)

`id`, `mrn`, `pesel` (zawsze), `firstName`, `lastName`, `birthDate`, `gender`, `status`, `flags`, `wardName` (tylko `admitted`), `bed` (tylko `admitted`).

### Przyjęcie

| DTO (ts) | Pola |
| --- | --- |
| `AdmitPatientRequest` (`AdmitPatientRequest`) | `admissionType` `AdmissionType` T, `admittedAt` Instant T, `wardId` UUID T (N dla `outpatient`), `room` N (<= 20), `bed` N (<= 20), `attendingPhysicianId` UUID T (rola `doctor`; N dla `outpatient`), `triageLevel` `TriageLevel` N, `reason` string T (niepusty; N dla `outpatient`), `referralNumber` N (<= 50) |
| `DischargePatientRequest` (`DischargePatientRequest`) | `dischargedAt` Instant T, `disposition` `DischargeDisposition` N, `summaryNoteId` UUID N, `version` long N (wersja aktywnego przyjęcia) |
| `AdmissionResponse` (`Admission`) | `id`, `patientId`, `encounterId`, `status` `AdmissionRecordStatus`, `admissionType`, `admittedAt`, `wardId` (opc. dla `outpatient`), `room`, `bed`, `attendingPhysicianId` (opc. dla `outpatient`), `triageLevel`, `reason`, `referralNumber`, `dischargedAt`, `dischargeDisposition`, `dischargeSummaryNoteId`, `version` |

## EHR

| DTO (ts) | Pola |
| --- | --- |
| `ClinicalNoteCreateRequest` (`ClinicalNoteCreateRequest`) | `patientId` N (zgodny ze ścieżką), `encounterId` N, `authorId` N (**ignorowane**), `category` `NoteCategory` T, `title` T (<= 200), `content` T (niepusty), `symptoms` string[] N (<= 50 elementów, każdy niepusty, <= 200) |
| `ClinicalNoteResponse` (`ClinicalNote`) | `id`, `patientId`, `encounterId`, `authorId`, `category`, `title`, `content`, `symptoms` (alfabetycznie, pomijane gdy puste), audyt, `version` |
| `DiagnosisCreateRequest` (`DiagnosisCreateRequest`) | `patientId` N, `encounterId` N, `code` `Coding` T, `type` `DiagnosisType` T, `status` `DiagnosisStatus` N (domyślnie `active`), `diagnosedAt` N (domyślnie teraz), `diagnosedById` N (**ignorowane**), `notes` N |
| `DiagnosisResponse` (`Diagnosis`) | `id`, `patientId`, `encounterId`, `code`, `type`, `status`, `diagnosedAt`, `diagnosedById`, `notes`, audyt, `version` |
| `AllergyCreateRequest` (`AllergyCreateRequest`) | `patientId` N, `substance` T (<= 200), `category` `AllergyCategory` T, `reaction` T (<= 500), `severity` `AllergySeverity` T, `status` `AllergyStatus` N (domyślnie `active`), `recordedAt` N (domyślnie teraz), `recordedById` N (**ignorowane**), `atcCodes` string[] N (<= 50, każdy niepusty, <= 10) |
| `AllergyResponse` (`Allergy`) | `id`, `patientId`, `substance`, `category`, `reaction`, `severity`, `status`, `recordedAt`, `recordedById`, `atcCodes` (alfabetycznie, pomijane gdy puste), audyt, `version` |
| `ContraindicationResponse` (`Contraindication`) | `id`, `patientId`, `description`, `reason`, `recordedAt` |
| `TreatmentResponse` (`Treatment`) | `id`, `patientId`, `encounterId`, `name`, `type` `TreatmentType`, `startAt`, `endAt`, `status` `TreatmentStatus`, `description`, `practitionerId` |
| `EncounterResponse` (`Encounter`) | `id`, `patientId`, `type` `EncounterType`, `status` `EncounterStatus`, `startAt`, `endAt`, `wardId`, `practitionerId` (opc. dla wizyt `outpatient`), `reason`, `summary`, `episodeId` (bez audytu) |
| `TreatmentEpisodeResponse` (`TreatmentEpisode`) | `id`, `patientId`, `title`, `startAt`, `endAt`, `status` `EpisodeStatus`, `diagnosisIds` UUID[] (zawsze, może być `[]`) |
| `EhrSummaryResponse` (`EhrSummary`) | `recentDiagnoses` `DiagnosisResponse[]`, `chronicConditions` `DiagnosisResponse[]`, `activeMedications` `ActiveMedicationResponse[]`, `recentEncounters` `EncounterResponse[]`, `allergies` `AllergyResponse[]` |

## Laboratorium

### Katalog

| DTO (ts) | Pola |
| --- | --- |
| `LabTestResponse` (`LabTest`) | `code`, `loinc` (opc.), `name`, `category` `LabCategory`, `specimenTypes` `SpecimenType[]` (kolejność enuma), `defaultSpecimen`, `turnaroundHours` int, `fastingRequired` boolean, `analytes` `LabAnalyteDefinitionResponse[]` (zawsze) |
| `LabAnalyteDefinitionResponse` (`LabAnalyteDefinition`) | `code`, `name`, `unit`, `low` (opc.), `high` (opc.) |
| `LabPanelResponse` (`LabPanel`) | `id` UUID, `name`, `testCodes` string[] (alfabetycznie) |

### Zlecenie

| DTO (ts) | Pola |
| --- | --- |
| `LabOrderCreateRequest` (`LabOrderCreateRequest`) | `patientId` N, `encounterId` N, `orderedById` N (**ignorowane**), `items` `LabOrderItemRequest[]` T (niepusta), `urgency` `Urgency` T, `fasting` boolean T, `plannedCollectionAt` Instant T, `diagnosisCode` `Coding` N, `clinicalInfo` T (niepusty), `notes` N |
| `LabOrderItemRequest` (`LabOrderItem`) | `testCode` T (niepusty), `specimenType` `SpecimenType` T; `id`, `testName`, `specimenId` ignorowane |
| `LabOrderResponse` (`LabOrder`) | `id`, `patientId`, `encounterId`, `orderedById`, `orderedAt`, `items` `LabOrderItemResponse[]`, `urgency`, `fasting`, `plannedCollectionAt`, `diagnosisCode`, `clinicalInfo`, `notes`, `status` `OrderStatus`, `statusHistory` `StatusChangeResponse[]`, audyt, `version` |
| `LabOrderItemResponse` (`LabOrderItem`) | `id`, `testCode`, `testName` (snapshot), `specimenId`, `specimenType` |

### Wynik

| DTO (ts) | Pola |
| --- | --- |
| `LabResultResponse` (`LabResult`) | `id`, `patientId`, `orderId`, `orderItemId`, `testCode`, `testName`, `category`, `collectedAt`, `resultedAt`, `status` `ResultStatus` (ts: `LabResultStatus`), `observations` `Observation[]` (po `analyteCode`), `performerName`, `comment`, `reviewedAt`, `reviewedById` |
| `LabResultResponse.Observation` (`LabObservation`) | `analyteCode`, `analyteName`, `value` (liczba **albo** string), `unit`, `referenceRange` `{ low?, high?, text? }` (zawsze obecny), `flag` `ObservationFlag` (ts: `ResultFlag`) |
| `LabResultWithPatientResponse` (`ResultWithPatient<LabResult>`) | pola `LabResultResponse` (spłaszczone) + `patient` `PatientSummaryResponse` |
| `AnalyteTrendResponse` (`AnalyteTrend`) | `analyteCode`, `analyteName`, `unit`, `low`, `high`, `points` `[ { at, value, flag } ]` (ts: `TrendPoint`) |
| `LabAnalyteRef` (brak typu TS; klient mapuje na `SelectOption`) | `code`, `name` |

## Obrazowanie

| DTO (ts) | Pola |
| --- | --- |
| `ImagingExamResponse` (`ImagingExam`) | `code`, `modality` `ImagingModality`, `name`, `bodyRegion`, `contrastPossible`, `requiresLaterality`, `preparation` (opc.), `durationMinutes` int |
| `ScheduleSlotResponse` (`ScheduleSlot`) | `id`, `modality`, `start`, `end` (Instant UTC), `room`, `available` boolean |
| `ImagingOrderCreateRequest` (`ImagingOrderCreateRequest`) | `patientId` N, `encounterId` N, `orderedById` N (**ignorowane**), `examCode` T (niepusty), `laterality` `Laterality` N (domyślnie `na`), `contrast` boolean T, `clinicalIndication` T (niepusty), `clinicalQuestion` N, `diagnosisCode` `Coding` N, `urgency` `Urgency` T, `safety` T, `slotId` UUID N |
| `ImagingOrderCreateRequest.Safety` (`SafetyChecklist`) | `pregnancy` `PregnancyStatus` T, `pacemakerOrImplant` T, `metalFragments` T, `contrastAllergy` T, `creatinine` N (>= 0, do 4+2 cyfr), `egfr` N (>= 0, do 5+1 cyfr), `claustrophobia` T, `confirmed` T (musi być `true`) - wszystkie boolean |
| `ImagingOrderResponse` (`ImagingOrder`) | `id`, `patientId`, `encounterId`, `examCode`, `examName`, `modality`, `bodyRegion`, `laterality`, `contrast`, `clinicalIndication`, `clinicalQuestion`, `diagnosisCode`, `urgency`, `safety` (jak wyżej; `creatinine`/`egfr` opc.), `slotId`, `scheduledAt`, `orderedById`, `orderedAt`, `status`, `statusHistory`, audyt, `version` |
| `ImagingResultResponse` (`ImagingResult`) | `id`, `patientId`, `orderId`, `modality`, `examName`, `bodyRegion`, `performedAt`, `reportedAt`, `radiologistName`, `radiologistId`, `technique`, `findings`, `conclusion`, `status` `ImagingResultStatus`, `imageCount` int, `critical` boolean, `reviewedAt`, `reviewedById` |
| `ImagingResultWithPatientResponse` (`ResultWithPatient<ImagingResult>`) | pola wyniku (spłaszczone) + `patient` |

## Leki i recepty

| DTO (ts) | Pola |
| --- | --- |
| `DrugResponse` (`Drug`) | `id`, `name`, `activeSubstance`, `atcCode`, `form` `DrugForm`, `strength`, `packageSize` int, `packageUnit`, `routes` `AdministrationRoute[]`, `defaultDoseUnit`, `rxOnly` boolean, `reimbursementOptions` `ReimbursementLevel[]`, `interactsWithAtc` string[] (opc.), `maxDailyDose` `{ value, unit }` (opc.; ts: `DoseQuantity`) |
| `PrescriptionCreateRequest` (`PrescriptionCreateRequest`) | `patientId` N, `encounterId` N, `prescriberId` N (**ignorowane**), `validFrom` LocalDate T, `validUntil` LocalDate T, `kind` `PrescriptionKind` T, `items` `PrescriptionItemRequest[]` T (niepusta), `notes` N |
| `PrescriptionItemRequest` (`PrescriptionItem`) | `drugId` UUID T, `dosage` `DosageRequest` T, `quantityPackages` int T (> 0), `reimbursement` `ReimbursementLevel` T, `substitutionAllowed` boolean T; snapshot (`drugName` itd.) ignorowany |
| `DosageRequest` (`DosageInstruction`) | `dose` T (> 0, do 7+3 cyfr), `doseUnit` T (niepusty, <= 30), `route` `AdministrationRoute` T, `frequency` `DoseFrequency` T, `timesOfDay` `TimeOfDay[]` N, `durationDays` int T (> 0), `asNeeded` boolean T, `maxPerDay` int N (> 0), `instructions` N |
| `PrescriptionCancelRequest` (`PrescriptionCancelRequest`) | `reason` N, `version` N |
| `PrescriptionResponse` (`Prescription`) | `id`, `patientId`, `encounterId`, `prescriberId`, `issuedAt`, `validFrom`, `validUntil`, `kind`, `items` `PrescriptionItemResponse[]` (po `drugName`), `status` `PrescriptionStatus` (efektywny), `accessCode` (4 cyfry), `eRxKey` (44 znaki; lokalny, dla `e_prescription` podmieniany kluczem e-receipt, patrz [rest-api-fhir.md](rest-api-fhir.md)), `notes`, `cancelledAt`, `cancelReason`, audyt, `version` |
| `PrescriptionItemResponse` (`PrescriptionItem`) | `id`, `drugId`, `drugName`, `activeSubstance`, `strength`, `form` (snapshot), `dosage` `DosageResponse`, `quantityPackages`, `reimbursement`, `substitutionAllowed` |
| `DosageResponse` (`DosageInstruction`) | `dose`, `doseUnit`, `route`, `frequency`, `timesOfDay` (kolejność enuma, opc.), `durationDays`, `asNeeded`, `maxPerDay` (opc.), `instructions` (opc.) |
| `ActiveMedicationResponse` (`ActiveMedication`) | pola `PrescriptionItemResponse` (płasko: `dosage`, ...) + `prescriptionId`, `date` (LocalDate = `validFrom` recepty) |
| `DrugSafetyCheckRequest` (`DrugSafetyCheckRequest`) | `patientId` UUID T, `drugId` N, `dosage` `DosageRequest` N, `items` `DrugSafetyItemRequest[]` N; wymagane `drugId` albo niepusta `items` |
| `DrugSafetyItemRequest` (brak TS) | `drugId` T, `dosage` N |
| `DrugSafetyWarningResponse` (`DrugSafetyWarning`) | `type` `DrugSafetyWarningType`, `severity` `DrugSafetySeverity`, `drugId`, `message` |

## Parametry życiowe

| DTO (ts) | Pola |
| --- | --- |
| `VitalSignsCreateRequest` (`VitalSignsCreateRequest`) | `patientId` N, `recordedAt` N (domyślnie teraz), `recordedById` N (**ignorowane**), `context` `VitalContext` T (ts: `VitalsContext`), `source` `VitalSource` N (ts: `VitalsSource`; domyślnie `manual`), `deviceId` N (<= 50, tylko `monitor`), `encounterId` N, `systolic`, `diastolic`, `heartRate`, `spo2`, `respiratoryRate`, `painScore` (liczby całkowite), `temperature` (1 miejsce po przecinku) N - min. jeden pomiar, `notes` N (<= 4000) |
| `VitalSignsResponse` (`VitalSigns`) | `id`, `patientId`, `recordedAt`, `recordedById`, `context`, `source`, `deviceId`, `encounterId`, `systolic`, `diastolic`, `heartRate` (int), `temperature` (liczba), `spo2`, `respiratoryRate`, `painScore` (int), `notes`; nieobecne pomiary pomijane |
| `VitalsRecordResponse` (`VitalsRecordResponse`) | `saved` `VitalSignsResponse`, `anomalies` `VitalAnomaly[]` |
| `VitalAnomaly` (`VitalAnomaly`) | `type` `VitalType`, `value`, `severity` `AnomalySeverity`, `direction` `AnomalyDirection`, `message`, `recordedAt` |
| `WardVitalsRow` (`WardVitalsRow`) | `patient` `PatientSummaryResponse`, `latest` `VitalSignsResponse` (opc.), `anomalies` `VitalAnomaly[]`, `lastMeasuredAgoMin` long (opc.) |
| `VitalThresholdResponse` (`VitalThreshold`) | `type` `VitalType`, `label`, `unit`, `low`, `high`, `criticalLow`, `criticalHigh`, `min`, `max` |

## Komunikacja i alerty

| DTO (ts) | Pola |
| --- | --- |
| `ThreadCreateRequest` (`ThreadCreateRequest`) | `participantIds` UUID[] T (może być puste), `subject` T (<= 200), `patientId` N, `firstMessage` `{ body T (niepusty), priority T }` T |
| `MessageSendRequest` (`MessageSendRequest`) | `body` T (niepusty), `priority` `Priority` T |
| `MessageThreadResponse` (`MessageThread`) | `id`, `participantIds`, `subject`, `patientId`, `createdById`, `lastMessageAt`, `unreadCount` long (`@viewerScoped`) |
| `MessageResponse` (`Message`) | `id`, `threadId`, `senderId`, `sentAt`, `body`, `priority`, `readByIds` UUID[] |
| `TaskCreateRequest` (`TaskCreateRequest`) | `title` T (<= 200), `description` N, `patientId` N, `assignedToId` UUID T, `createdById` N (**ignorowane**), `dueAt` N, `priority` `Priority` T, `status` N (tylko `open`) |
| `TaskStatusUpdateRequest` (`TaskStatusUpdateRequest`) | `status` `TaskStatus` T, `version` N |
| `TeamTaskResponse` (`TeamTask`) | `id`, `title`, `description`, `patientId`, `assignedToId`, `createdById`, `createdAt`, `dueAt`, `priority`, `status`, `updatedAt`, `updatedById`, `version` |
| `HandoffNoteCreateRequest` (`HandoffNoteCreateRequest`) | `wardId` T, `shiftDate` LocalDate T, `shift` `ShiftType` T, `fromId` N (**ignorowane**), `toId` T, `generalNotes` N, `patientNotes` `HandoffPatientNoteDto[]` T (może być puste) |
| `HandoffPatientNoteDto` (`HandoffPatientNote`) | `patientId` T, `situation` T, `background` T, `assessment` T, `recommendation` T (niepuste) |
| `HandoffNoteResponse` (`HandoffNote`) | `id`, `wardId`, `shiftDate`, `shift`, `fromId`, `toId`, `createdAt`, `generalNotes`, `patientNotes` (po `patientId`) |
| `AlertResponse` (`ClinicalAlert`; bez `link`) | `id`, `type` `AlertType`, `severity` `AlertSeverity`, `patientId`, `message`, `createdAt`, `acknowledged` boolean (zawsze, `@viewerScoped`), `acknowledgedById`, `acknowledgedAt`, `target` `AlertTargetDto` |
| `AlertTargetDto` (`AlertTarget`) | `kind` `AlertTargetKind`, `id` UUID, `patientId` (opc.) |

## Terminologia

| DTO | Pola |
| --- | --- |
| `SnomedConceptPage` | `total` int, `offset` int, `concepts` `SnomedConcept[]` |
| `SnomedConcept` | `code` (SCTID), `display` |

## Enumy

Wartości na drucie (`wire()`), w kolejności deklaracji w kodzie (tą kolejnością sortowane są listy-zbiory). Nazwa Java = nazwa typu TS, chyba że podano inaczej.

| Enum Java (ts) | Wartości na drucie |
| --- | --- |
| `StaffRole` | `doctor`, `nurse`, `lab_technician`, `radiologist`, `pharmacist`, `registrar`, `admin` |
| `StaffAccountStatus` | `pending`, `active`, `locked` |
| `Gender` | `female`, `male`, `other`, `unknown` |
| `BloodType` | `A+`, `A-`, `B+`, `B-`, `AB+`, `AB-`, `0+`, `0-` |
| `PatientStatus` (ts: `AdmissionStatus`) | `registered`, `admitted`, `outpatient`, `discharged` |
| `PatientFlag` | `isolation`, `fall_risk`, `dnr`, `infection_risk`, `vip` |
| `NoPeselReason` | `foreigner`, `newborn`, `unknown_identity` |
| `IdentityDocumentType` | `id_card`, `passport`, `other` |
| `InsuranceStatus` | `active`, `inactive`, `unknown` |
| `InsurancePayer` | `NFZ`, `private`, `none` |
| `AdmissionType` | `planned`, `emergency`, `transfer`, `outpatient` |
| `AdmissionRecordStatus` | `active`, `discharged`, `cancelled` |
| `TriageLevel` | `red`, `orange`, `yellow`, `green`, `blue` |
| `DischargeDisposition` | `home`, `transfer`, `deceased`, `against_advice`, `other` |
| `EncounterType` | `visit`, `consultation`, `hospitalization`, `emergency`, `teleconsultation` |
| `EncounterStatus` | `planned`, `in_progress`, `finished`, `cancelled` |
| `EpisodeStatus` | `active`, `closed` |
| `CodingSystem` | `ICD-10`, `LOINC`, `ATC`, `ICD-9-PL`, `local` |
| `NoteCategory` | `admission`, `progress`, `consultation`, `nursing`, `observation`, `discharge` |
| `DiagnosisType` | `primary`, `secondary`, `chronic` |
| `DiagnosisStatus` | `active`, `resolved` |
| `AllergyCategory` | `drug`, `food`, `environment`, `other` |
| `AllergySeverity` | `mild`, `moderate`, `severe`, `life_threatening` |
| `AllergyStatus` | `active`, `inactive` |
| `TreatmentType` | `pharmacotherapy`, `procedure`, `surgery`, `rehabilitation`, `other` |
| `TreatmentStatus` | `ongoing`, `completed`, `discontinued` |
| `OrderStatus` | `ordered`, `scheduled`, `specimen_collected`, `in_progress`, `completed`, `cancelled` |
| `Urgency` (ts: `OrderUrgency`) | `routine`, `urgent`, `stat` |
| `LabCategory` | `hematology`, `biochemistry`, `coagulation`, `immunology`, `urinalysis`, `microbiology`, `pathology` |
| `SpecimenType` | `blood`, `serum`, `urine`, `stool`, `swab`, `csf`, `tissue` |
| `ResultStatus` (ts: `LabResultStatus`) | `preliminary`, `final`, `corrected` |
| `ObservationFlag` (ts: `ResultFlag`) | `N`, `L`, `H`, `LL`, `HH`, `A` |
| `ResultAbnormalityFilter` | `all`, `abnormal`, `critical` |
| `ImagingModality` | `USG`, `RTG`, `CT`, `MRI`, `MMG`, `ENDOSCOPY`, `COLONOSCOPY`, `ANGIOGRAPHY` |
| `Laterality` | `left`, `right`, `bilateral`, `na` |
| `PregnancyStatus` | `no`, `yes`, `unknown`, `na` |
| `ImagingResultStatus` | `preliminary`, `final` |
| `DrugForm` | `tablet`, `capsule`, `injection`, `syrup`, `drops`, `ointment`, `inhaler`, `suppository`, `patch` |
| `AdministrationRoute` | `oral`, `sublingual`, `iv`, `im`, `sc`, `topical`, `inhalation`, `rectal`, `transdermal` |
| `ReimbursementLevel` | `100%`, `50%`, `30%`, `R`, `B`, `none` |
| `PrescriptionKind` | `e_prescription`, `hospital_order` |
| `PrescriptionStatus` | `issued`, `partially_dispensed`, `dispensed`, `cancelled`, `expired` |
| `DoseFrequency` | `QD`, `BID`, `TID`, `QID`, `Q4H`, `Q6H`, `Q8H`, `Q12H`, `QW`, `PRN` |
| `TimeOfDay` | `morning`, `noon`, `evening`, `night` |
| `DrugSafetyWarningType` | `allergy`, `interaction`, `duplicate`, `max_dose` |
| `DrugSafetySeverity` | `warn`, `danger` |
| `VitalType` | `systolic`, `diastolic`, `heartRate`, `temperature`, `spo2`, `respiratoryRate` |
| `VitalContext` (ts: `VitalsContext`) | `office_exam`, `ward_round`, `triage`, `observation` |
| `VitalSource` (ts: `VitalsSource`) | `manual`, `monitor` |
| `VitalsRange` | `24h`, `7d`, `30d`, `all` |
| `AnomalySeverity` | `warning`, `critical` |
| `AnomalyDirection` | `low`, `high` |
| `Priority` | `normal`, `high`, `critical` |
| `TaskStatus` | `open`, `in_progress`, `done`, `cancelled` |
| `ShiftType` | `day`, `night` |
| `AlertType` | `critical_result`, `vital_anomaly`, `order_status`, `task`, `system` |
| `AlertSeverity` | `info`, `warning`, `critical` |
| `AlertTargetKind` | `lab_result`, `imaging_result`, `patient_vitals`, `lab_order`, `imaging_order`, `task`, `patient` |
| `ApiErrorCode` (nie `WireEnum`: wartość = nazwa stałej) | `NOT_FOUND`, `VALIDATION_FAILED`, `CONFLICT`, `FORBIDDEN`, `UNAUTHENTICATED`, `INTERNAL` |

Uwagi:

- Flagi obserwacji: `N` normalna, `L`/`H` poza zakresem, `LL`/`HH` krytyczne (tylko jawnie), `A` nieprawidłowy wynik tekstowy; `abnormal` w filtrze = flaga różna od `N`.
- Alerty tworzone przez backend: typy `critical_result`, `vital_anomaly`, `order_status`, `task` oraz cele `lab_result`, `imaging_result`, `patient_vitals`, `lab_order`, `imaging_order`, `task` ([events.md](events.md)). Wartości `AlertType.system` i `AlertTargetKind.patient` istnieją w enumie, ale w kodzie nic ich nie tworzy.
- Klucze w `errors[].field` dla parametrów życiowych to wartości `VitalType` (`heartRate`, `spo2`, ...).
