# ERD kontraktu HIS (tekstowe)

Źródło: `apps/his_frontend/src/app/models/*.model.ts` i `models/api/*.ts` (stan po krokach 1-11). Konwencje ogólne (ID, daty, enumy, audyt, wersjonowanie) są w [CONVENTIONS.md](CONVENTIONS.md) i nie są tu powtarzane. Endpointy: [API.md](API.md).

## Legenda

| Oznaczenie | Znaczenie |
| --- | --- |
| PK / FK / UQ | klucz główny / obcy / unikalność |
| `@snapshot` | kopia z chwili zdarzenia, zapisana w wierszu (kolumna), nie aktualizuje się przy zmianie źródła |
| `@projection` | wartość liczona przez backend, nie jest kolumną (chyba że zaznaczono "stored") |
| `@viewerScoped` | wartość liczona per zalogowany użytkownik (nie cache'owana globalnie) |
| `[S]` | pole nadawane przez backend (klient go nie wysyła): id, audyt, version, status początkowy itp. |
| `[N]` | "nowe w kontrakcie (opcjonalne)": pole dodane w krokach 1-11, w TS opcjonalne; istniejące od początku nie mają znacznika |
| `[E]` | `@Embeddable` (kolumny z prefiksem w tabeli właściciela) |
| `[C]` | `@ElementCollection` / tabela pomocnicza |

Mapowanie typów (domyślne, dalej skracane):

| TS | Java | SQL (PostgreSQL) |
| --- | --- | --- |
| `ID` | `UUID` | `uuid` |
| `string` | `String` | `varchar(n)` / `text` |
| `number` (liczba całkowita) | `int` / `Integer` | `integer` |
| `number` (wartość pomiarowa) | `BigDecimal` | `numeric(p,s)` |
| `boolean` | `boolean` | `boolean` |
| `ISODate` | `LocalDate` | `date` |
| `ISODateTime` | `Instant` | `timestamptz` |
| unia stringów | `enum` + `@JsonValue` | `varchar` + `CHECK` (lub typ enum) |

Wspólne pola bazowe (nie powtarzane w każdej tabeli):

- `Auditable` = `createdAt` (S), `createdById` (S, FK StaffMember), `updatedAt` (S), `updatedById` (S). W TS bywa `Partial<Auditable>` - w bazie kolumny istnieją zawsze.
- `Versioned` = `version` (S, `@Version` Long, `bigint`).
- Kolumny audytu i `version` są w encjach: Patient, Admission (tylko version), ClinicalNote, Diagnosis, Allergy, LabOrder, ImagingOrder, Prescription, TeamTask (Partial). Encje niezmienne (VitalSigns, LabResult, ImagingResult, Message, ClinicalAlert) mają tylko znaczniki czasu z własnych pól.

## Diagram relacji (skrót)

```
Ward 1──N StaffMember 1──1 UserAccount
Ward 1──N Admission N──1 Patient
Patient 1──N Admission ;  Admission 1──1 Encounter(type=hospitalization)  [właściciel: Admission.encounterId]
Patient 1──N Encounter N──0..1 TreatmentEpisode N──M Diagnosis (episode_diagnosis)
Patient 1──N ClinicalNote / Diagnosis / Allergy / Contraindication / Treatment
Encounter 1──N ClinicalNote / Diagnosis / Treatment / LabOrder / ImagingOrder / Prescription / VitalSigns (opcjonalne encounterId)
LabTest 1──N LabAnalyteDefinition ; LabPanel N──M LabTest (lab_panel_test)
LabOrder 1──N LabOrderItem ; LabOrder 1──N StatusChange ; LabOrder 1──N LabResult(orderId opc.) ; LabOrderItem 1──N LabResult(orderItemId opc.)
LabResult 1──N LabObservation
ImagingExam 1──N ImagingOrder ; ScheduleSlot 0..1──1 ImagingOrder ; ImagingOrder 1──N StatusChange ; ImagingOrder 1──N ImagingResult
Drug 1──N PrescriptionItem N──1 Prescription
Patient 1──N VitalSigns ; VitalThreshold = konfiguracja (bez FK)
MessageThread 1──N ThreadParticipant N──1 StaffMember ; MessageThread 1──N Message
ClinicalAlert 1──N AlertAcknowledgement N──1 StaffMember
StaffMember 1──N TeamTask (assignedTo/createdBy) ; Ward 1──N HandoffNote 1──N HandoffPatientNote
```

---

## 1. Staff / auth

### Ward (tabela `ward`)

| Pole | TS | Java / SQL | Uwagi |
| --- | --- | --- | --- |
| id | `ID` | `UUID` PK | S |
| name | `string` | `varchar(200)` | |
| shortName | `string` | `varchar(10)` | UQ (proponowane) |
| floor | `string` | `varchar(10)` | piętro jako tekst (np. `'0'`) |
| beds | `number` | `int` | liczba łóżek; `0` dla poradni. Docelowo może być `@projection` = `count(Bed)` |

### Bed (opcjonalne, po MVP)

Model TS nie zawiera encji łóżka: `Admission.room` i `Admission.bed` są stringami. Jeśli backend doda tabelę `bed` (`id`, `wardId` FK, `room`, `label`, UQ(`wardId`,`room`,`label`)), API zachowuje stringi (`room`, `bed`), a `Ward.beds` staje się projekcją. Brak typu w `models/` - encja wyłącznie backendowa.

### StaffMember (`staff_member`)

| Pole | TS | Java / SQL | Uwagi |
| --- | --- | --- | --- |
| id | `ID` | `UUID` PK | S |
| title | `string` | `varchar(50)` | np. `lek.`, `mgr piel.` |
| firstName, lastName | `string` | `varchar(100)` | |
| role | `StaffRole` | enum `StaffRole` | `doctor`, `nurse`, `lab_technician`, `radiologist`, `pharmacist`, `registrar`, `admin` (rozszerzone w krokach 2-11) |
| specialization | `string?` | `varchar(100)` null | |
| wardId | `ID` | FK `ward.id` | N:1 |
| phone | `string?` | `varchar(30)` null | |
| pwz | `string?` | `varchar(7)` null | UQ (nullable, UQ częściowe: `WHERE pwz IS NOT NULL`), 7 cyfr |
| employeeId | `string?` | `varchar(30)` null | UQ, login. Dla kont z logowaniem wymagany |
| email | `string?` | `varchar(200)` null | UQ (proponowane, nullable) |
| accountStatus | `StaffAccountStatus?` | enum `pending`/`active`/`locked` | `[N]`; fizycznie w `user_account`, tu projekcja |
| online | `boolean` | - | `@projection` obecność (WebSocket sessions), nie kolumna |

### UserAccount (`user_account`, encja tylko backendowa)

Brak typu w `models/`; kontrakt widoczny przez `LoginRequest`, `LoginResponse`, `CurrentUser`, `StaffRegistrationRequest/Response`.

| Pole | Typ Java / SQL | Uwagi |
| --- | --- | --- |
| id | `UUID` PK | |
| staffId | FK `staff_member.id` | 1:1, UQ |
| employeeId | `varchar(30)` | UQ, login (`LoginRequest.employeeId`); kopia lub join do `staff_member.employeeId` |
| passwordHash | `varchar(100)` | BCrypt/Argon2; nigdy w odpowiedziach |
| accountStatus | enum `StaffAccountStatus` | rejestracja tworzy `pending`; aktywuje administrator |
| failedAttempts, lockedUntil, lastLoginAt | `int`, `Instant`, `Instant` | polityka blokady |
| permissions | `@viewerScoped` `string[]` | `CurrentUser.permissions?` - wyliczane z roli |

`CurrentUser` = `StaffMember` + `permissions` (projekcja sesji, `GET /auth/me`).

---

## 2. Pacjent i przyjęcie

### Patient (`patient`) - Auditable (tylko `createdById`, `updatedById` w TS + `createdAt/updatedAt`), Versioned

| Pole | TS | Java / SQL | Uwagi |
| --- | --- | --- | --- |
| id | `ID` | `UUID` PK | S |
| mrn | `string` | `varchar(30)` | S, UQ, NOT NULL; format `HIS/2026/000123` (sekwencja) |
| pesel | `string \| null` | `varchar(11)` null | UQ częściowy (`WHERE pesel IS NOT NULL`), `null` = brak (znaczenie semantyczne) |
| noPeselReason | `NoPeselReason?` | enum null | `foreigner`/`newborn`/`unknown_identity`; `[N]`; wymagane gdy `pesel == null` (walidacja 422) |
| identityDocument | `IdentityDocument?` | `[E]` `identity_doc_type`, `identity_doc_number` | `[N]` |
| firstName, lastName | `string` | `varchar(100)` | |
| secondName | `string?` | `varchar(100)` null | |
| birthDate | `ISODate` | `LocalDate` | |
| gender | `Gender` | enum | `female`/`male`/`other`/`unknown` |
| phone, email | `string?` | `varchar` null | |
| address | `Address` | `[E]` | wymagany |
| emergencyContact | `EmergencyContact?` | `[E]` | `[N]` |
| insurance | `Insurance` | `[E]` | wymagany |
| bloodType | `BloodType?` | enum + `@JsonValue` | `'0+'` itd. |
| status | `AdmissionStatus` | enum `registered`/`admitted`/`outpatient`/`discharged` | stored, utrzymywany przez backend przy przyjęciu/wypisie (nie wysyłany w PATCH jako autorytatywny); patrz regułę niżej |
| currentAdmission | `Admission?` | - | `@projection` aktywnego `Admission` (status `active`); nie kolumna |
| flags | `PatientFlag[]` | `[C]` `patient_flag(patientId, flag)` | `isolation`, `fall_risk`, `dnr`, `infection_risk`, `vip` |
| version | `number?` | `@Version` | S |
| createdAt/updatedAt/createdById/updatedById | | | S |

Osadzenia (`@Embeddable`):

- `Address` (`street`, `buildingNumber`, `apartmentNumber?`, `postalCode`, `city`, `country`) - kolumny `address_*`; `Address` używany tylko w Patient.
- `IdentityDocument` (`type`: `IdentityDocumentType` = `id_card`/`passport`/`other`, `number`).
- `EmergencyContact` (`fullName`, `relation`, `phone`, `isLegalGuardian`).
- `Insurance` (`status`: `InsuranceStatus` = `active`/`inactive`/`unknown`, `nfzBranch`, `payer`: `InsurancePayer` = `NFZ`/`private`/`none`, `ewusVerifiedAt?`).

Reguły:

- `PatientDraft` (alias `PatientCreateRequest`) = Patient bez `id, mrn, createdAt, updatedAt, createdById, updatedById, version`. Klient nie wysyła `status` logicznie jako źródła prawdy, ale pole pozostaje w typie: backend ignoruje i ustawia `registered` przy rejestracji (do potwierdzenia).
- `PatientUpdateRequest` = `Partial<PatientDraft> & { version? }`; PATCH: brak pola = bez zmian, `null` = wyczyść.
- `PatientSummary` = projekcja (patrz sekcja 11).
- Duplikat PESEL: `PatientDuplicateCheckResponse` = `PatientSummary | null`.

### Admission (`admission`) - Versioned; WŁAŚCICIEL relacji Admission <-> Encounter

Proces administracyjny ADT. Pacjent ma wiele przyjęć (historia), **co najwyżej jedno `active`** (UQ częściowy: `UNIQUE(patient_id) WHERE status = 'active'`).

| Pole | TS | Java / SQL | Uwagi |
| --- | --- | --- | --- |
| id | `ID?` | `UUID` PK | S (w TS opcjonalne bo `AdmitPatientRequest` go nie zawiera) |
| patientId | `ID?` | FK `patient.id` | S (z ścieżki) |
| encounterId | `ID?` | FK `encounter.id`, UQ | S; 1:1 z Encounter `type=hospitalization`; Admission jest właścicielem FK. Dla `admissionType='outpatient'` backend może utworzyć Encounter `visit` lub zostawić null (do potwierdzenia) |
| status | `AdmissionRecordStatus?` | enum `active`/`discharged`/`cancelled` | S; nie mylić z `Patient.status` (`AdmissionStatus`) |
| admissionType | `AdmissionType` | enum `planned`/`emergency`/`transfer`/`outpatient` | |
| admittedAt | `ISODateTime` | `Instant` | |
| wardId | `ID` | FK `ward.id` | |
| room, bed | `string?` | `varchar(20)` null | patrz Bed |
| attendingPhysicianId | `ID` | FK `staff_member.id` | rola `doctor` |
| triageLevel | `TriageLevel?` | enum `red`/`orange`/`yellow`/`green`/`blue` | |
| reason | `string` | `text` | |
| referralNumber | `string?` | `varchar(50)` null | |
| dischargedAt | `ISODateTime?` | `Instant` null | S (z `DischargePatientRequest`) |
| dischargeDisposition | `DischargeDisposition?` | enum `home`/`transfer`/`deceased`/`against_advice`/`other` | |
| dischargeSummaryNoteId | `ID?` | FK `clinical_note.id` null | epikryza (notatka `discharge`) |
| version | `number?` | `@Version` | |

Reguły stanu (backend): przyjęcie (`admitPatient`) ustawia `Patient.status` = `outpatient` (dla `admissionType='outpatient'`) albo `admitted`; wypis -> `discharged`. Przyjęcie pacjenta, który ma aktywne przyjęcie: 409 `CONFLICT` (mock zamyka poprzednie - nie jest specyfikacją). `currentAdmission` jest **projekcją** - zwracana w `Patient` i nigdy zapisywana. Historia: `GET /patients/{id}/admissions`. `AdmitPatientRequest` = Admission bez `id, patientId, encounterId, status, dischargedAt, dischargeDisposition, dischargeSummaryNoteId, version`.

---

## 3. EHR

### Encounter (`encounter`)

| Pole | TS | Java / SQL | Uwagi |
| --- | --- | --- | --- |
| id | `ID` | `UUID` PK | S |
| patientId | `ID` | FK | |
| type | `EncounterType` | enum | `visit`/`consultation`/`hospitalization`/`emergency`/`teleconsultation` |
| status | `EncounterStatus` | enum | `planned`/`in_progress`/`finished`/`cancelled` |
| startAt, endAt | `ISODateTime`, `?` | `Instant` | |
| wardId | `ID?` | FK null | |
| practitionerId | `ID` | FK `staff_member` | |
| reason | `string` | `text` | |
| summary | `string?` | `text` null | |
| episodeId | `ID?` | FK `treatment_episode` null | N:1 |

Encounter 1—N: ClinicalNote, Diagnosis, Treatment, LabOrder, ImagingOrder, Prescription, VitalSigns (przez opcjonalne `encounterId` po stronie dzieci). Dla `hospitalization` 1:1 z Admission (FK po stronie Admission). Brak `Auditable`/`Versioned` w TS (encja tylko do odczytu w obecnym kontrakcie; backend dodaje audyt wewnętrznie).

### TreatmentEpisode (`treatment_episode`) + `episode_diagnosis`

| Pole | TS | Java / SQL |
| --- | --- | --- |
| id | `ID` | `UUID` PK |
| patientId | `ID` | FK |
| title | `string` | `varchar(200)` |
| startAt, endAt | `ISODateTime`, `?` | `Instant` |
| status | `EpisodeStatus` | enum `active`/`closed` |
| diagnosisIds | `ID[]` | M:N `episode_diagnosis(episode_id, diagnosis_id)`, PK złożony; `@ManyToMany` po stronie TreatmentEpisode (właściciel) |

### ClinicalNote (`clinical_note`) - Auditable, Versioned

`id`, `patientId` FK, `encounterId?` FK, `authorId` FK StaffMember (w żądaniu; docelowo z sesji - patrz CONVENTIONS), `category` (`NoteCategory`: `admission`/`progress`/`consultation`/`nursing`/`observation`/`discharge`), `title` `varchar(200)`, `content` `text`, `symptoms?` `string[]` `[C]` `clinical_note_symptom(note_id, symptom)`. `ClinicalNoteCreateRequest` = bez `id` i audytu/`version`.

### Diagnosis (`diagnosis`) - Partial<Auditable>, Versioned

`id`, `patientId`, `encounterId?`, `code: Coding` `[E]` (`code_system`, `code_value`, `code_display`; `@snapshot` display), `type` (`DiagnosisType`: `primary`/`secondary`/`chronic`), `status` (`DiagnosisStatus`: `active`/`resolved`), `diagnosedAt`, `diagnosedById` FK, `notes?`. `Coding`: `system: CodingSystem` (`'ICD-10'`, `'LOINC'`, `'ATC'`, `'ICD-9-PL'`, `'local'`), `code`, `display`. Słownik ICD-10: `getIcd10Dictionary` (dane referencyjne, tabela `icd10_code` lub import).

### Allergy (`allergy`) - Partial<Auditable>, Versioned

`id`, `patientId`, `substance` `varchar`, `category` (`AllergyCategory`: `drug`/`food`/`environment`/`other`), `reaction`, `severity` (`AllergySeverity`: `mild`/`moderate`/`severe`/`life_threatening`), `status` (`AllergyStatus`: `active`/`inactive`), `recordedAt`, `recordedById?` (z sesji), `atcCodes?` `string[]` `[C]` `allergy_atc_code(allergy_id, atc_code)` (używane przez kontrolę bezpieczeństwa leku).

### Contraindication (`contraindication`)

`id`, `patientId` FK, `description`, `reason`, `recordedAt`. Brak audytu/wersji w TS (tylko odczyt w kontrakcie; brak endpointu zapisu).

### Treatment (`treatment`)

`id`, `patientId`, `encounterId?`, `name`, `type` (`TreatmentType`: `pharmacotherapy`/`procedure`/`surgery`/`rehabilitation`/`other`), `startAt`, `endAt?`, `status` (`TreatmentStatus`: `ongoing`/`completed`/`discontinued`), `description`, `practitionerId` FK. Tylko odczyt w kontrakcie.

`EhrSummary` - projekcja (sekcja 11).

---

## 4. Laboratorium

### LabTest (`lab_test`, katalog) - klucz naturalny `code`

| Pole | TS | Java / SQL |
| --- | --- | --- |
| code | `string` | `varchar(30)` PK (lub UQ + UUID PK; kontrakt używa `code` jako identyfikatora) |
| loinc | `string?` | `varchar(20)` null |
| name | `string` | `varchar(200)` |
| category | `LabCategory` | enum: `hematology`, `biochemistry`, `coagulation`, `immunology`, `urinalysis`, `microbiology`, `pathology` |
| specimenTypes | `SpecimenType[]` | `[C]` `lab_test_specimen(test_code, specimen_type)` |
| defaultSpecimen | `SpecimenType` | enum: `blood`/`serum`/`urine`/`stool`/`swab`/`csf`/`tissue` (musi należeć do `specimenTypes`) |
| turnaroundHours | `number` | `int` |
| fastingRequired | `boolean` | `boolean` |
| analytes | `LabAnalyteDefinition[]` | 1:N `lab_analyte_definition` |

`LabAnalyteDefinition` (`lab_analyte_definition`): `code`, `name`, `unit`, `low?`, `high?` (`numeric`), FK `test_code`; UQ(`test_code`,`code`).

### LabPanel (`lab_panel`) + `lab_panel_test`

`id: string` (w TS zwykły `string`, nie `ID`; w bazie `UUID` lub klucz naturalny - **do potwierdzenia**, nie zmieniamy nazwy pola), `name`, `testCodes: string[]` -> M:N `lab_panel_test(panel_id, test_code)` (LabPanel właściciel).

### LabOrder (`lab_order`) - Versioned, Partial<Auditable>

| Pole | TS | Java / SQL | Uwagi |
| --- | --- | --- | --- |
| id | `ID` | `UUID` PK | S |
| patientId | `ID` | FK | |
| encounterId | `ID?` | FK null | |
| orderedById | `ID` | FK StaffMember | docelowo z sesji (S) |
| orderedAt | `ISODateTime` | `Instant` | S |
| items | `LabOrderItem[]` | 1:N `lab_order_item` | `@OneToMany(cascade=ALL, orphanRemoval)` |
| urgency | `OrderUrgency` | enum `routine`/`urgent`/`stat` | |
| fasting | `boolean` | | |
| plannedCollectionAt | `ISODateTime` | `Instant` | |
| diagnosisCode | `Coding?` | `[E]` | |
| clinicalInfo | `string` | `text` | |
| notes | `string?` | `text` null | |
| status | `OrderStatus` | enum | S; zmieniany tylko akcjami (`status`/`cancel`); wartości: `ordered`, `scheduled`, `specimen_collected`, `in_progress`, `completed`, `cancelled` |
| statusHistory | `StatusChange[]` | 1:N `lab_order_status_change` | S, append-only, `@OrderBy at` |

`LabOrderItem` (`lab_order_item`): `id?` (PK UUID, `[N]`, S), `testCode` FK `lab_test.code`, `testName` `@snapshot`, `specimenId?` (`[N]`, poza zakresem - bez encji `Specimen`, kolumna opcjonalnie), `specimenType` (`SpecimenType`).

`StatusChange` (wspólny typ dla lab i imaging; tabele `lab_order_status_change` i `imaging_order_status_change`, albo jedna `order_status_change` z `order_type`): `status`, `at`, `byId?` (z sesji), `note?`, FK `order_id`. Wpis początkowy (`ordered`) tworzony przy `POST`.

`LabOrderDraft` (deprecated) = `LabOrderCreateRequest` = LabOrder bez `id, orderedAt, status, statusHistory, version` i audytu. **Uwaga:** `orderedById` jest nadal w typie żądania (UI go wysyła); backend ignoruje/waliduje względem sesji.

### LabResult (`lab_result`) - niezmienny poza `reviewed*`

| Pole | TS | Java / SQL | Uwagi |
| --- | --- | --- | --- |
| id | `ID` | `UUID` PK | S |
| patientId | `ID` | FK | |
| orderId | `ID?` | FK `lab_order` null | wymagane dla zleceń wewnętrznych, opcjonalne dla zewnętrznych |
| orderItemId | `ID?` | FK `lab_order_item` null | `[N]` |
| testCode | `string` | FK `lab_test.code` | |
| testName | `string` | `@snapshot` | |
| category | `LabCategory` | enum | kopia z testu w chwili wyniku |
| collectedAt, resultedAt | `ISODateTime` | `Instant` | |
| status | `LabResultStatus` | enum `preliminary`/`final`/`corrected` | |
| observations | `LabObservation[]` | 1:N `lab_observation` | |
| performerName | `string` | `@snapshot` | |
| comment | `string?` | `text` null | |
| reviewedAt, reviewedById | `ISODateTime?`, `ID?` | null | `[N]`; ustawiane przez akcję `acknowledge` (`ResultReview`) |

`LabObservation` (`lab_observation`): `analyteCode` FK, `analyteName` `@snapshot`, `value: number | string` (Java: kolumny `value_numeric numeric`, `value_text varchar` - dokładnie jedna wypełniona; w JSON liczba lub string), `unit`, `referenceRange: ReferenceRange` `[E]` (`ref_low`, `ref_high`, `ref_text`), `flag: ResultFlag` (`N`, `L`, `H`, `LL`, `HH`, `A`; `LL`/`HH` = krytyczne).

`ResultReview` (`{reviewedAt, reviewedById}`): opis semantyki pól `reviewed*` na `LabResult`/`ImagingResult` - nie osobna tabela.

Typy pochodne: `TrendPoint`, `AnalyteTrend` - projekcje (sekcja 11). `ResultAbnormalityFilter` (`all`/`abnormal`/`critical`) - parametr zapytania.

---

## 5. Obrazowanie

### ImagingExam (`imaging_exam`, katalog) - klucz `code`

`code` PK, `modality` (`ImagingModality`: `USG`, `RTG`, `CT`, `MRI`, `MMG`, `ENDOSCOPY`, `COLONOSCOPY`, `ANGIOGRAPHY`), `name`, `bodyRegion`, `contrastPossible`, `requiresLaterality`, `preparation?`, `durationMinutes`.

### ScheduleSlot (`schedule_slot`) - poza priorytetem kontraktu

`id`, `modality`, `start`, `end` (`Instant`), `room`, `available`. `GET /imaging/slots?modality&date` (`SlotQuery`). Backend modeluje z modułem grafiku; `ImagingOrder.slotId` FK (N:1, slot 0..1 zlecenie; UQ na `slot_id` gdy `available=false`).

### ImagingOrder (`imaging_order`) - Versioned, Partial<Auditable>

| Pole | TS | Java / SQL | Uwagi |
| --- | --- | --- | --- |
| id, patientId, encounterId? | | | |
| examCode | `string` | FK `imaging_exam.code` | |
| examName, modality, bodyRegion | `string`/enum/`string` | `@snapshot` | kopie z katalogu w chwili zlecenia |
| laterality | `Laterality` | enum `left`/`right`/`bilateral`/`na` | |
| contrast | `boolean` | | |
| clinicalIndication | `string` | `text` | |
| clinicalQuestion | `string?` | `text` null | |
| diagnosisCode | `Coding?` | `[E]` | |
| urgency | `OrderUrgency` | enum | |
| safety | `SafetyChecklist` | `[E]` osadzony | `pregnancy` (`PregnancyStatus`: `no`/`yes`/`unknown`/`na`), `pacemakerOrImplant`, `metalFragments`, `contrastAllergy`, `creatinine?`, `egfr?` (`numeric`), `claustrophobia`, `confirmed` |
| slotId | `ID?` | FK `schedule_slot` null | |
| scheduledAt | `ISODateTime?` | `Instant` null | |
| orderedById, orderedAt | | | orderedById z sesji; orderedAt S |
| status, statusHistory | `OrderStatus`, `StatusChange[]` | | jak w LabOrder (`specimen_collected` nie dotyczy obrazowania) |

`ImagingOrderDraft` (deprecated) = `ImagingOrderCreateRequest` = ImagingOrder bez `id, orderedAt, status, statusHistory, version` i audytu.

### ImagingResult (`imaging_result`) - niezmienny poza `reviewed*`

`id`, `patientId`, `orderId?` FK (wymagane dla wewnętrznych), `modality`, `examName`, `bodyRegion` (kopie), `performedAt`, `reportedAt`, `radiologistName` `@snapshot`, `radiologistId?` FK (`[N]`), `technique?`, `findings` `text`, `conclusion` `text`, `status` (`ImagingResultStatus`: `preliminary`/`final`), `imageCount` `int`, `critical` `boolean` (flaga ustawiana przez radiologa, nie wyliczana), `reviewedAt?`, `reviewedById?` (`[N]`).

> Załączniki DICOM, obrazy i pliki załączników z założenia nie są implementowane w tej aplikacji (brak encji `Attachment`, brak `studyInstanceUid`); `imageCount` służy tylko do wyświetlania.

---

## 6. Leki i recepty

### Drug (`drug`, katalog, tylko odczyt z UI)

`id` UUID PK, `name` (handlowa), `activeSubstance`, `atcCode`, `form` (`DrugForm`: `tablet`, `capsule`, `injection`, `syrup`, `drops`, `ointment`, `inhaler`, `suppository`, `patch`), `strength`, `packageSize` `int`, `packageUnit`, `routes` (`AdministrationRoute[]`: `oral`, `sublingual`, `iv`, `im`, `sc`, `topical`, `inhalation`, `rectal`, `transdermal`) `[C]`, `defaultDoseUnit`, `rxOnly`, `reimbursementOptions` (`ReimbursementLevel[]`: `'100%'`, `'50%'`, `'30%'`, `'R'`, `'B'`, `'none'`) `[C]`, `interactsWithAtc?` `string[]` `[C]`, `maxDailyDose?` (`DoseQuantity` `{value, unit}` `[E]`). Indeksy: `atcCode`, `lower(name)`, `lower(activeSubstance)`.

### Prescription (`prescription`) - Versioned, Partial<Auditable>

| Pole | TS | Java / SQL | Uwagi |
| --- | --- | --- | --- |
| id | `ID` | `UUID` PK | S |
| patientId, encounterId? | | FK | |
| prescriberId | `ID` | FK StaffMember (`doctor`) | docelowo z sesji |
| issuedAt | `ISODateTime` | | S |
| validFrom, validUntil | `ISODate` | `LocalDate` | |
| kind | `PrescriptionKind` | enum `e_prescription`/`hospital_order` | |
| items | `PrescriptionItem[]` | 1:N `prescription_item` | |
| status | `PrescriptionStatus` | enum | S: `issued`, `partially_dispensed`, `dispensed`, `cancelled`, `expired` (`expired` może być projekcją od `validUntil`) |
| accessCode | `string` | `char(4)` | S, 4 cyfry |
| eRxKey | `string` | `char(44)` | S, mock klucz; w produkcji z P1/e-recepty |
| notes | `string?` | | |
| cancelledAt, cancelReason | `?` | null | S (akcja `cancel`) |

`PrescriptionItem` (`prescription_item`): `id?` (S), `drugId` FK `drug`, **snapshot**: `drugName`, `activeSubstance`, `strength`, `form` (`@snapshot` - zamrożone w wierszu, późniejsza zmiana katalogu nie zmienia recepty), `dosage: DosageInstruction` `[E]`, `quantityPackages` `int`, `reimbursement` (`ReimbursementLevel`), `substitutionAllowed`.

`DosageInstruction` `[E]` (kolumny `dosage_*`): `dose` (`numeric`), `doseUnit`, `route` (`AdministrationRoute`), `frequency` (`DoseFrequency`: `QD`, `BID`, `TID`, `QID`, `Q4H`, `Q6H`, `Q8H`, `Q12H`, `QW`, `PRN`), `timesOfDay?` (`TimeOfDay[]`: `morning`/`noon`/`evening`/`night`) `[C]`, `durationDays` `int`, `asNeeded`, `maxPerDay?`, `instructions?`. Używany też w `DrugSafetyCheckRequest.dosage?`.

`PrescriptionDraft` (deprecated) = `PrescriptionCreateRequest` = Prescription bez `id, issuedAt, status, accessCode, eRxKey, version`, audytu i `cancelledAt/cancelReason`.

`ActiveMedication`, `DrugSafetyWarning` - projekcje (sekcja 11). `DrugSafetyWarningType` (`allergy`/`interaction`/`duplicate`/`max_dose`), `DrugSafetySeverity` (`warn`/`danger`).

---

## 7. Parametry życiowe

### VitalSigns (`vital_signs`) - niezmienny (korekta = nowy odczyt), brak `version`

Płaski wiersz z nullable kolumnami (świadoma decyzja; bez EAV):

| Pole | TS | Java / SQL | Uwagi |
| --- | --- | --- | --- |
| id | `ID` | `UUID` PK | S |
| patientId | `ID` | FK, indeks (`patient_id`, `recorded_at desc`) | |
| recordedAt | `ISODateTime` | `Instant` | |
| recordedById | `ID` | FK | z sesji (S docelowo); `VitalSignsDraft` nadal go zawiera |
| context | `VitalsContext` | enum `office_exam`/`ward_round`/`triage`/`observation` | |
| source | `VitalsSource?` | enum `manual`/`monitor`, brak = `manual` | `[N]` |
| deviceId | `string?` | `varchar(50)` null | `[N]`, gdy `source='monitor'` |
| encounterId | `ID?` | FK null | |
| systolic, diastolic, heartRate, spo2, respiratoryRate | `number?` | `smallint` null | |
| temperature | `number?` | `numeric(4,1)` null | |
| painScore | `number?` | `smallint` null | poza `VitalType`, bez progów |
| notes | `string?` | `text` null | |

Walidacja: wartości w granicach `min`/`max` z `VitalThreshold` (422), co najmniej jeden pomiar wypełniony.

### VitalThreshold (`vital_threshold`, konfiguracja)

`type` (`VitalType`: `systolic`, `diastolic`, `heartRate`, `temperature`, `spo2`, `respiratoryRate`) PK, `label`, `unit`, `low`, `high`, `criticalLow`, `criticalHigh`, `min`, `max` (`numeric`). Źródło: `GET /vital-thresholds` (brak metody serwisu - obecnie stałe w `constants/vitals-thresholds.ts`). Backend wylicza anomalie z tej tabeli (tabela jest kontraktem; stałe front = tylko mock).

`VitalsContext`, `VitalsSource`, `VitalsRange` (`24h`/`7d`/`30d`/`all` - parametr zapytania), `AnomalySeverity` (`warning`/`critical`), `AnomalyDirection` (`low`/`high`) - enumy/parametry. `VitalAnomaly`, `VitalsRecordResponse`, `WardVitalsRow` - projekcje (sekcja 11 i API).

---

## 8. Komunikacja

### MessageThread (`message_thread`)

`id`, `subject` `varchar(200)`, `patientId?` FK null, `createdById?` (S, sesja), `lastMessageAt` (stored, aktualizowane przy wiadomości), `participantIds: ID[]` -> `@projection` z `thread_participant`, `unreadCount: number` -> `@viewerScoped` = liczba wiadomości z `sentAt > lastReadAt` zalogowanego (bez własnych).

### ThreadParticipant (`thread_participant`) - typ dokumentacyjny w `message.model.ts`

PK złożony (`threadId`, `staffId`): `threadId` FK, `staffId` FK, `lastReadAt?` (kursor odczytu), `joinedAt`. To jest źródło stanu "przeczytane" (zastępuje `Message.readByIds`).

### Message (`message`) - niezmienna

`id`, `threadId` FK, `senderId` FK (z sesji), `sentAt` (S), `body` `text`, `priority` (`Priority`: `normal`/`high`/`critical`), `readByIds: ID[]` -> `@projection` z `ThreadParticipant.lastReadAt` (`@deprecated` w TS; backend może zwracać dla zgodności). Brak `Auditable` (świadomie - `sentAt`/`senderId` pełnią tę rolę).

### ClinicalAlert (`clinical_alert`) + AlertAcknowledgement (`alert_acknowledgement`)

`ClinicalAlert`: `id`, `type` (`AlertType`: `critical_result`, `vital_anomaly`, `order_status`, `task`, `system`), `severity` (`AlertSeverity`: `info`/`warning`/`critical`), `patientId?` FK, `message`, `createdAt` (S), `target?` (`AlertTarget` `[E]`: `kind` `AlertTargetKind` = `lab_result`/`imaging_result`/`patient_vitals`/`lab_order`/`imaging_order`/`task`/`patient`, `id`, `patientId?`; `[N]`; typowany wskaźnik bez ścieżek UI), `link?` (`@deprecated`; backend nie zna tras UI - nie zapisuje), `acknowledged` (`@viewerScoped`), `acknowledgedById?`, `acknowledgedAt?` (`@viewerScoped` - dane potwierdzenia zalogowanego użytkownika).

`AlertAcknowledgement` (encja backendowa): PK (`alertId`, `staffId`), `acknowledgedAt`. Potwierdzenie jest per użytkownik (alert może być potwierdzony przez jednego, a nadal aktywny dla innych - **do potwierdzenia**, czy potwierdzenie jednej osoby wycisza globalnie). `AlertCreateRequest` jest wewnętrzny (server-to-server), klient nie tworzy alertów.

### TeamTask (`team_task`) - Partial<Auditable>

`id`, `title`, `description?`, `patientId?` FK, `assignedToId` FK, `createdById` FK (z sesji), `createdAt`, `dueAt?`, `priority` (`Priority`), `status` (`TaskStatus`: `open`/`in_progress`/`done`/`cancelled`). `TaskCreateRequest` = TeamTask bez `id, createdAt`.

### HandoffNote (`handoff_note`) + HandoffPatientNote (`handoff_patient_note`)

`HandoffNote`: `id`, `wardId` FK, `shiftDate` `LocalDate`, `shift` (`ShiftType`: `day`/`night`), `fromId` FK (z sesji), `toId` FK, `createdAt` (S), `generalNotes?`, `patientNotes` 1:N. `HandoffPatientNote` (osadzana kolekcja, PK (`handoffId`, `patientId`)): `patientId` FK, `situation`, `background`, `assessment`, `recommendation` (SBAR; `text`). `HandoffNoteCreateRequest` = HandoffNote bez `id, createdAt`.

---

## 9. Typy wspólne / transportowe (nie encje)

`ID`, `ISODate`, `ISODateTime` (aliasy), `Ref` (opcjonalna projekcja `{id, display}`), `Auditable`, `Versioned` (mixiny, patrz wyżej), `Page`, `PageQuery`, `SortDirection`, `FieldError`, `ApiErrorCode`, `ProblemDetail` (transport błędów).

Typy żądań i zapytań (`models/api`) - opisane w [API.md](API.md): `LoginRequest`, `LoginResponse`, `StaffRegistrationRequest`, `StaffRegistrationResponse`, `CurrentUser`, `StaffQuery`, `PatientCreateRequest`, `PatientUpdateRequest`, `PatientSearchQuery`, `PatientDuplicateCheckResponse`, `AdmitPatientRequest`, `DischargePatientRequest`, `DischargeOptions`, `AdmissionQuery`, `ClinicalNoteCreateRequest`, `DiagnosisCreateRequest`, `AllergyCreateRequest`, `OrderStatusUpdateRequest`, `OrderCancelRequest`, `ResultAcknowledgeRequest`, `LabOrderCreateRequest`, `LabOrderQuery`, `LabOrderFilter`, `LabResultQuery`, `ResultWithPatient`, `ImagingOrderCreateRequest`, `ImagingOrderQuery`, `ImagingOrderFilter`, `ImagingResultQuery`, `SlotQuery`, `PrescriptionCreateRequest`, `PrescriptionQuery`, `PrescriptionFilter`, `PrescriptionCancelRequest`, `DrugSafetyCheckRequest`, `DrugSearchQuery`, `VitalSignsCreateRequest`, `VitalsRecordResponse`, `VitalsQuery`, `WardVitalsQuery`, `ThreadQuery`, `ThreadCreateRequest`, `MessageSendRequest`, `ThreadMarkReadRequest`, `TaskQuery`, `TaskCreateRequest`, `TaskStatusUpdateRequest`, `HandoffNoteCreateRequest`, `AlertQuery`, `AlertCreateRequest`, `AlertAcknowledgeRequest`; aliasy `@deprecated`: `PatientDraft`, `LabOrderDraft`, `ImagingOrderDraft`, `PrescriptionDraft`, `VitalSignsDraft`.

## 10. Typy, które NIE są kontraktem (UI)

| Typ | Plik | Powód |
| --- | --- | --- |
| `TableColumn<T>` | `table.model.ts` | konfiguracja kolumn tabeli PrimeNG (`field`, `header`, `type`, `tagKind`, `sortable`, `width`) |
| `TagKind` (re-eksport) | `table.model.ts` / `constants/tag-severity.ts` | kolor znacznika UI |
| `TagSeverity` | `common.model.ts` | severity znacznika PrimeNG |
| `ToastSeverity` | `common.model.ts` | severity toastów PrimeNG |
| `SelectOption<T>` | `common.model.ts` | `{label, value}` dla list wyboru; backend zwraca dane domenowe (np. kod + nazwa analitu), mapowanie po stronie klienta |

## 11. Projekcje i widoki (nie tabele)

Backend jest autorytatywny (kod w mockach to tylko implementacja przykładowa).

| Typ | Opis | Z czego backend składa |
| --- | --- | --- |
| `PatientSummary` | odchudzony pacjent na listy: `id, mrn, pesel, firstName, lastName, birthDate, gender, status, flags` + `wardName?`, `bed?` (tylko `admitted`) | `Patient` + aktywny `Admission` (+ `Ward.name`) |
| `EhrSummary` | `recentDiagnoses` (5 najnowszych wg `diagnosedAt`), `chronicConditions` (`type='chronic'`), `activeMedications`, `recentEncounters` (5 wg `startAt`), `allergies` | Diagnosis, Encounter, Allergy, aktywne pozycje recept |
| `ActiveMedication` | `PrescriptionItem` + `prescriptionId`, `date` (= `validFrom`) | pozycje recept w statusie `issued`/`partially_dispensed` z `validUntil >= dziś` |
| `WardVitalsRow` | `patient: PatientSummary`, `latest?: VitalSigns`, `anomalies: VitalAnomaly[]`, `lastMeasuredAgoMin?` (`@viewerScoped`/względem "teraz") | pacjenci `admitted` w oddziale + ostatni `VitalSigns` + `VitalThreshold`; sortowanie: krytyczne, ostrzeżenia, reszta |
| `VitalAnomaly` | `type, value, severity, direction, message, recordedAt` | porównanie odczytu z `VitalThreshold` (`low/high` = `warning`, `criticalLow/criticalHigh` = `critical`) |
| `VitalsRecordResponse` | `{ saved: VitalSigns, anomalies: VitalAnomaly[] }` | wynik `POST /patients/{id}/vitals` |
| `DrugSafetyWarning` | `type, severity, drugId?, message` | alergie (`Allergy.substance`/`atcCodes`), aktywne leki (duplikat substancji, interakcje `Drug.interactsWithAtc`), `Drug.maxDailyDose` vs `DosageInstruction` |
| `DashboardStats` | liczniki: `admittedPatients`, `newResults`, `criticalAlerts`, `openTasks`, `pendingOrders`, `vitalsAnomalies` | zliczenia z Patient, LabResult (nowe), ClinicalAlert (`critical`, niepotwierdzone przez usera), TeamTask (`open`, przypisane do usera), LabOrder+ImagingOrder w toku, `WardVitalsRow` z anomaliami |
| `AnalyteTrend`, `TrendPoint` | seria wartości (`points`) jednego analitu pacjenta (`at`, `value`, `flag?`) + zakres referencyjny | `LabResult.observations` (tylko wartości liczbowe), posortowane po `collectedAt` |
| `ResultWithPatient<T>` | wynik + `patient: PatientSummary` (inbox) | join Patient |
| `CurrentUser` | `StaffMember` + `permissions?` | sesja |
| `MessageThread.participantIds/unreadCount`, `Message.readByIds`, `Patient.currentAdmission`, `StaffMember.online` | pola-projekcje w encjach | j.w. |

## 12. Weryfikacja kompletności typów

Każdy typ eksportowany z `models/*.model.ts` i `models/api/*.ts` (186 eksportów) występuje w tym dokumencie w sekcjach 1-9 (encje i enumy, typy wspólne i żądań), 10 (typy UI) albo 11 (projekcje). Sprawdzono skryptem grep (każda nazwa typu występuje w ERD.md lub API.md).
