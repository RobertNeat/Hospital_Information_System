# User roles

Roles available in the system, what each one is for, and what the person working in that role is
responsible for.

## Administrator

Manages user accounts and the hospital's organizational structure. Approves new staff registrations,
locks accounts when needed, and can unlock them. Manages the staff list and wards. Can view patient
records, orders, results and prescriptions, admit and discharge patients, and set vital sign alert
thresholds. The administrator does not write clinical notes, diagnoses, orders or prescriptions - that is
the responsibility of clinical roles.

**Account registration and activation:** a new staff member registers themselves, providing their details
and intended role. The account is created in a pending state and cannot be used to log in until an
administrator approves it. The administrator reviews pending registrations and activates the ones they
consider valid. The administrator can also lock an active account (for example when a staff member leaves,
or if unauthorized access is suspected) and unlock a previously locked account - using the same action as
activating a new one. An account also locks automatically after several failed login attempts; this kind
of lock expires on its own after a short time, or can be lifted manually by an administrator.

## Doctor

Manages patient treatment: admits and discharges patients, maintains clinical documentation (notes,
diagnoses, allergies), orders and cancels laboratory and imaging tests, confirms their results, and issues
and cancels prescriptions. Reads and records vital signs, receives and acknowledges clinical alerts, and
takes part in team tasks.

## Nurse

Cares for the patient on the ward: reviews admissions, maintains nursing notes and records allergies,
reviews laboratory orders and confirms specimen collection. Reviews imaging results and prescriptions,
records vital signs, takes part in team tasks, and receives and acknowledges clinical alerts.

## Lab technician

Handles laboratory orders: reviews orders, updates their status as the test progresses (for example after
specimen collection, during analysis, once the test is complete), and enters results. Has limited access to
patient records (only what is needed to carry out the test), can view the drug catalog, and receives
clinical alerts.

## Radiologist

Handles imaging orders: reviews orders, updates their status as the exam progresses, and enters results
together with findings. Has limited access to patient records and consultation notes, and receives
clinical alerts.

## Pharmacist

Reviews prescriptions: checks issued prescriptions, the drug catalog, and drug interactions and safety for
a given patient. Has limited access to patient records.

## Registrar

Registers new patients in the system and handles their admission to a ward or outpatient clinic.

## For every role

Regardless of role, every user can: view patient records, read and send messages, view the staff list and
wards, and use the dashboard.

## Logging in

Logging in requires an active account and the correct password. After several failed login attempts, the
account is temporarily locked (see the Administrator section). In addition, the server limits how many
login attempts can come from a single IP address within a given time window, independently of the account
lock.

## Demo accounts

Accounts that are always loaded, including in production - intended only for demos and testing (login and
password are the same). All accounts belong to a demo ward.

| Login            | Role           |
| ---------------- | -------------- |
| `admin`          | administrator  |
| `user`, `doctor` | doctor         |
| `nurse`          | nurse          |
| `lab-tech`       | lab technician |
| `radiologist`    | radiologist    |
| `pharmacist`     | pharmacist     |
| `registrar`      | registrar      |

Passwords for these accounts are trivial and public (they are stored in the repository). Before using the
system with real patient data, these accounts should be locked or their passwords changed.

## Extended demo accounts (sample test data)

Ten staff members assigned to specific, realistic wards - used only in a development/demo environment with
extended test data. Logins follow the pattern `EMP-0001`...`EMP-0010`, with a shared password
`HisDemo2026!`.

| Login    | Staff member        | Role       | Ward                    |
| -------- | ------------------- | ---------- | ----------------------- |
| EMP-0001 | Anna Nowak          | doctor     | Internal medicine       |
| EMP-0002 | Piotr Wiśniewski    | doctor     | Cardiology              |
| EMP-0003 | Tomasz Kamiński     | doctor     | General surgery         |
| EMP-0004 | Marta Lewandowska   | doctor     | Neurology               |
| EMP-0005 | Paweł Zieliński     | doctor     | Emergency medicine (ER) |
| EMP-0006 | Katarzyna Zielińska | nurse      | Internal medicine       |
| EMP-0007 | Magdalena Wójcik    | nurse      | Cardiology              |
| EMP-0008 | Ewa Lewandowska     | head nurse | Surgery                 |
| EMP-0009 | Agnieszka Kaczmarek | nurse      | Neurology               |
| EMP-0010 | Michał Szymański    | nurse      | ER                      |
