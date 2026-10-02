--liquibase formatted sql

--changeset his:011-diagnosis-code-system-snomed
ALTER TABLE diagnosis DROP CONSTRAINT ck_diagnosis_code_system;
ALTER TABLE diagnosis ADD CONSTRAINT ck_diagnosis_code_system
    CHECK (code_system IN ('ICD-10', 'LOINC', 'ATC', 'ICD-9-PL', 'local', 'SNOMED'));

--changeset his:011-lab-order-diagnosis-code-system-snomed
ALTER TABLE lab_order DROP CONSTRAINT ck_lab_order_diagnosis_code_system;
ALTER TABLE lab_order ADD CONSTRAINT ck_lab_order_diagnosis_code_system
    CHECK (diagnosis_code_system IS NULL OR diagnosis_code_system IN
        ('ICD-10', 'LOINC', 'ATC', 'ICD-9-PL', 'local', 'SNOMED'));

--changeset his:011-imaging-order-diagnosis-code-system-snomed
ALTER TABLE imaging_order DROP CONSTRAINT ck_imaging_order_diagnosis_code_system;
ALTER TABLE imaging_order ADD CONSTRAINT ck_imaging_order_diagnosis_code_system
    CHECK (diagnosis_code_system IS NULL OR diagnosis_code_system IN
        ('ICD-10', 'LOINC', 'ATC', 'ICD-9-PL', 'local', 'SNOMED'));
