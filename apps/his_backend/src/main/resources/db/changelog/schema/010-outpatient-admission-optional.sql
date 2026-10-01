--liquibase formatted sql

--changeset his:010-admission-outpatient-optional
-- Przyjecie ambulatoryjne moze nie miec oddzialu ani lekarza prowadzacego (reguly wymusza PatientService).
ALTER TABLE admission ALTER COLUMN ward_id DROP NOT NULL;
ALTER TABLE admission ALTER COLUMN attending_physician_id DROP NOT NULL;
ALTER TABLE encounter ALTER COLUMN practitioner_id DROP NOT NULL;
