# Main process flows

A description of the system's main process flows: patient registration, laboratory and imaging orders,
e-prescriptions, clinical documentation, vital signs and alerts, team communication, and user account
management. Each flow has a goal, its input assumptions, its steps, and its outcome.

## Patient registration and admission

**Goal:** register a new patient in the system and admit them to a ward or to outpatient care.

**Assumptions:**

- The user has permission to register patients (registrar or administrator).
- No patient with the given national ID number exists yet in the system.

**Steps:**

1. The user fills in the 4-step registration wizard (Identity, Personal data, Insurance, Admission).
   While the national ID number is being entered, the system checks in the background whether a matching
   patient already exists.
2. The system saves the patient's data - a new record is created with status "registered".
3. The user fills in the admission details (ward, doctor, reason - not required for outpatient care).
4. The system saves a visit and an admission linked to that visit.

**Outcome:**

- The patient's status is "admitted" (hospitalization) or "outpatient".
- If the admission step fails, the patient remains registered but without an admission; the user is
  informed of this and can retry the admission.

## Placing a laboratory order

**Goal:** a doctor orders a laboratory test, a lab technician carries it out and enters the result, and
the e-laboratory integration keeps the order status in sync between the two systems.

**Assumptions:**

- The doctor has permission to place laboratory orders.
- The nurse has permission to confirm specimen collection.
- The lab technician has permission to change the order status and enter results.
- The e-laboratory integration is enabled in both directions, and mTLS authentication is active.
- The clinical indication with a SNOMED CT code is optional.

**Steps:**

1. The doctor fills in the laboratory order wizard (test type, urgency, fasting requirement, clinical
   indication).
2. The system saves the order with the initial status "ordered" and automatically forwards it to
   e-laboratory. A failed delivery is retried automatically at regular intervals.
3. A nurse or lab technician confirms specimen collection.
4. The lab technician updates the order status in e-laboratory as the test progresses (during analysis,
   once the test is complete). Every change is first confirmed by the main system.
5. The lab technician enters the test result in e-laboratory.

**Outcome:**

- The result is recorded in the main system the same way as a result entered manually.
- Once the result is final and every item on the order has an approved result, the order automatically
  moves to status "completed".
- Order statuses: ordered -> scheduled / specimen collected -> in progress -> completed (intermediate
  stages can be skipped), or cancelled from any stage that is not yet completed.
- Result statuses: preliminary, final, corrected (only final and corrected count as approved).

## Placing an imaging order

**Goal:** a doctor orders an imaging exam, a radiologist carries it out and enters the result, and the
e-imaging integration keeps the order status in sync between the two systems.

**Assumptions:**

- The doctor has permission to place imaging orders.
- The radiologist has permission to change the order status and enter results.
- The e-imaging integration is enabled in both directions, and mTLS authentication is active.

**Steps:**

1. The doctor fills in the order wizard (exam, modality, body region, laterality, contrast use, clinical
   indication, and a patient safety checklist covering pregnancy, metal implants, contrast allergy and
   claustrophobia).
2. The system saves the order with the initial status "ordered" and automatically forwards it to
   e-imaging. A failed delivery is retried automatically at regular intervals.
3. The radiologist updates the order status in e-imaging as the exam progresses. Every change is first
   confirmed by the main system.
4. The radiologist enters the result (findings, conclusion, critical flag).

**Outcome:**

- The result is recorded in the main system; once it is final, the order automatically moves to status
  "completed".
- A result flagged as critical also raises a clinical alert for the staff caring for the patient.
- Order and result statuses are the same as for laboratory orders.

## Issuing an e-prescription

**Goal:** a doctor issues a prescription with a drug safety check, and the e-receipt integration forwards
it to the external dispensing system.

**Assumptions:**

- The doctor has permission to issue and cancel prescriptions.
- The patient's allergies and contraindications (if any) are documented in the system.

**Steps:**

1. The doctor fills in the prescription wizard, picking drugs from the catalog and setting the dosage.
2. The system checks for interactions between the prescribed drugs and the patient's existing allergies
   and contraindications, and warns the doctor of any risk found.
3. The doctor saves the prescription.
4. The system automatically forwards the prescription to e-receipt. A failed delivery is retried
   automatically in the background.

**Outcome:**

- The prescription is fully valid in the main system regardless of the outcome of the delivery to
  e-receipt.
- Dispensing a drug to the patient (fully or partially) is recorded in e-receipt and reflected back in the
  main system.
- The doctor can cancel the prescription as long as it has not yet been fully dispensed.
- Prescription statuses: issued -> partially dispensed -> dispensed, or cancelled from any stage that is
  not yet fully dispensed. A prescription whose validity period has passed without being dispensed is
  treated as expired.

## Clinical documentation during a visit

**Goal:** maintain a patient's clinical documentation (notes, diagnoses, allergies) during a visit or
hospitalization.

**Assumptions:**

- The doctor and the nurse have permission to maintain documentation within the scope of their role (see
  [userRoles_en.md](userRoles_en.md)).
- The patient has an active visit or hospitalization.

**Steps:**

1. The doctor or nurse adds a clinical note of the relevant category (for example an admission note,
   progress note, consultation note, nursing note, or discharge note).
2. The doctor records a diagnosis with a SNOMED CT code.
3. The doctor or nurse records a patient allergy, if one is found.

**Outcome:**

- The note, diagnosis or allergy is saved in the patient's record.
- Diagnoses and allergies are visible immediately to all staff with access to the patient's record, which
  allows the system to, for example, warn automatically when a prescription is being issued.

## Vital signs and alerts

**Goal:** record a patient's vital signs and automatically detect values outside the normal range.

**Assumptions:**

- The doctor or nurse has permission to record vital signs.
- Warning and critical thresholds are configured for the measurement type being recorded.

**Steps:**

1. The doctor or nurse records a measurement (blood pressure, heart rate, oxygen saturation, respiratory
   rate, temperature, pain score).
2. The system compares the recorded value against the warning and critical thresholds for that
   measurement type.
3. If a threshold is crossed, the system raises a clinical alert visible to the staff caring for the
   patient.
4. A staff member acknowledges the alert.

**Outcome:**

- The measurement is saved as a new, immutable entry - correcting a mistaken reading does not overwrite
  the existing entry, it adds a new one.
- Crossing the warning threshold and the critical threshold each raise a separate alert.
- Acknowledging the alert records who responded and when.

## Team communication: messages, tasks, shift handoff

**Goal:** exchange information and coordinate staff work, including handing over patient care between
shifts.

**Assumptions:**

- Every role has permission to use messaging and team tasks.

**Steps:**

1. A user creates a message thread, optionally linked to a specific patient, and sets a priority
   (normal/high/critical).
2. A user creates a team task and assigns it to another staff member.
3. When a shift changes on a ward, a nurse or doctor creates a shift handoff note listing the patients
   that need the next shift's attention.

**Outcome:**

- The message is visible to the thread's participants; each participant has their own last-read marker.
- The team task has a progress status tracked independently of other tasks.
- The handoff note is visible to the next shift's staff, with a comment for each patient.

## User account management

**Goal:** register a new staff account and have it approved by an administrator, and lock or unlock
accounts as needed.

**Assumptions:**

- Registration is open to anyone who provides the required details.
- Activating, locking and unlocking an account is restricted to the administrator.

**Steps:**

1. A new staff member registers themselves, providing their details and intended role.
2. The system creates the account in a pending state - the account cannot be used to log in.
3. The administrator reviews pending registrations and activates the ones they consider valid.
4. When needed, the administrator locks an active account (for example when a staff member leaves) or
   unlocks a previously locked one.

**Outcome:**

- An activated account can be used to log in.
- A locked account cannot be used to log in, regardless of whether the password is correct.
- Full description of roles and the login mechanism: [userRoles_en.md](userRoles_en.md#administrator).
