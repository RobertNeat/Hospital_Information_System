<p align="center">
  <img src="apps/his_frontend/public/logo.png" width="150" alt="HIS logo">
</p>

<h1 align="center">Hospital Information System (HIS)</h1>

_Wersja polska: [docs/readme/README_pl.md](docs/readme/README_pl.md)_

Hospital Information System (HIS) is an enterprise-grade clinical platform built around medical-grade
information standards from the ground up. It brings patient care, diagnostics and prescribing into one
coherent workflow, speaking the same terminology and data-exchange languages used by real-world healthcare
systems - SNOMED CT, HL7 FHIR R4, and ATC drug classification. The result is a system designed to behave
like the backbone of a modern hospital's IT system.

## Enterprise standards used

- **SNOMED CT** - the world's most comprehensive clinical terminology, used for diagnoses and clinical
  indications, with ICD-10 codes derived on demand.
- **ATC (Anatomical Therapeutic Chemical)** - the WHO drug classification standard, powering the drug
  catalog and automated interaction checks.
- **HL7 FHIR R4** - the leading healthcare interoperability standard, used for every integration with
  external prescribing, laboratory and imaging systems.
- **mTLS (mutual TLS, X.509)** - mutual certificate authentication securing every service-to-service
  integration.
- **OpenAPI** - a live, always-current REST API specification.

## What HIS does

- **Patient registration & admissions** - a guided registration wizard with live duplicate detection,
  followed by admission to a ward or outpatient care.
- **Clinical documentation (EHR)** - notes, diagnoses and allergies, organized per patient and per visit.
- **Laboratory orders** - from ordering a test to tracking its progress and reviewing the result.
- **Imaging orders** - exam scheduling, safety checklists, and radiologist findings.
- **E-prescriptions** - issuing, dispensing and cancelling prescriptions, with drug interaction and
  reimbursement checks.
- **Vital signs monitoring** - continuous recording with configurable alert thresholds.
- **Clinical alerts & messaging** - critical results, vital sign anomalies and team communication in one
  place.
- **Team tasks & shift handoff** - coordinating work and patient handover between shifts and wards.
- **Role-based dashboards** - every staff role sees the information and actions relevant to their job.

See [docs/readme/userRoles_en.md](docs/readme/userRoles_en.md) for what each role can do.

## Documentation

| Document                                                             | Contents                                                           |
| -------------------------------------------------------------------- | ------------------------------------------------------------------ |
| [docs/readme/userRoles_en.md](docs/readme/userRoles_en.md)           | Roles, what each one can do, demo accounts                         |
| [docs/readme/localDeploy_en.md](docs/readme/localDeploy_en.md)       | Ports, first-time setup, running locally (with and without Docker) |
| [docs/readme/envVariables_en.md](docs/readme/envVariables_en.md)     | Environment variables used across the project                      |
| [docs/readme/databaseSchema_en.md](docs/readme/databaseSchema_en.md) | Entity schema, relationships, initial data                         |
| [docs/readme/services_en.md](docs/readme/services_en.md)             | Services, communication between them, API documentation            |
| [docs/readme/basicWorkflows_en.md](docs/readme/basicWorkflows_en.md) | Main process flows across the system                               |

Production deployment documentation (CI/CD, server): `.github/pipeline_docs/production_deployment.md`.

---

This project references SNOMED Clinical Terms® (SNOMED CT®), a clinical terminology maintained by SNOMED
International (www.snomed.org). No SNOMED CT content is included in this repository. Users wishing to work
with real SNOMED CT data must obtain their own license via MLDS (mlds.ihtsdotools.org).
