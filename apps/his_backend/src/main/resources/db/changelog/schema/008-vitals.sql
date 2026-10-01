--liquibase formatted sql

--changeset his:008-vital-signs
CREATE TABLE vital_signs (
    id               uuid         NOT NULL,
    patient_id       uuid         NOT NULL,
    recorded_at      timestamptz  NOT NULL,
    recorded_by_id   uuid         NOT NULL,
    context          varchar(15)  NOT NULL,
    source           varchar(10)  NOT NULL DEFAULT 'manual',
    device_id        varchar(50),
    encounter_id     uuid,
    systolic         smallint,
    diastolic        smallint,
    heart_rate       smallint,
    spo2             smallint,
    respiratory_rate smallint,
    temperature      numeric(4,1),
    pain_score       smallint,
    notes            text,
    CONSTRAINT pk_vital_signs PRIMARY KEY (id),
    CONSTRAINT fk_vital_signs_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_vital_signs_recorded_by FOREIGN KEY (recorded_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_vital_signs_encounter FOREIGN KEY (encounter_id) REFERENCES encounter (id),
    CONSTRAINT ck_vital_signs_context CHECK (context IN ('office_exam', 'ward_round', 'triage', 'observation')),
    CONSTRAINT ck_vital_signs_source CHECK (source IN ('manual', 'monitor')),
    CONSTRAINT ck_vital_signs_any_measurement CHECK (
        systolic IS NOT NULL OR diastolic IS NOT NULL OR heart_rate IS NOT NULL OR spo2 IS NOT NULL
        OR respiratory_rate IS NOT NULL OR temperature IS NOT NULL OR pain_score IS NOT NULL)
);
CREATE INDEX ix_vital_signs_patient_recorded_at ON vital_signs (patient_id, recorded_at DESC);
CREATE INDEX ix_vital_signs_recorded_by_id ON vital_signs (recorded_by_id);
CREATE INDEX ix_vital_signs_encounter_id ON vital_signs (encounter_id);
