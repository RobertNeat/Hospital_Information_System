--liquibase formatted sql

--changeset his:003-clinical-note
CREATE TABLE clinical_note (
    id            uuid         NOT NULL,
    patient_id    uuid         NOT NULL,
    encounter_id  uuid,
    author_id     uuid         NOT NULL,
    category      varchar(15)  NOT NULL,
    title         varchar(200) NOT NULL,
    content       text         NOT NULL,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by_id uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by_id uuid,
    version       bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_clinical_note PRIMARY KEY (id),
    CONSTRAINT fk_clinical_note_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_clinical_note_encounter FOREIGN KEY (encounter_id) REFERENCES encounter (id),
    CONSTRAINT fk_clinical_note_author FOREIGN KEY (author_id) REFERENCES staff_member (id),
    CONSTRAINT fk_clinical_note_created_by FOREIGN KEY (created_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_clinical_note_updated_by FOREIGN KEY (updated_by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_clinical_note_category CHECK (category IN
        ('admission', 'progress', 'consultation', 'nursing', 'observation', 'discharge'))
);
CREATE INDEX ix_clinical_note_patient_id ON clinical_note (patient_id, created_at DESC);
CREATE INDEX ix_clinical_note_encounter_id ON clinical_note (encounter_id);
CREATE INDEX ix_clinical_note_author_id ON clinical_note (author_id);
CREATE INDEX ix_clinical_note_created_by_id ON clinical_note (created_by_id);
CREATE INDEX ix_clinical_note_updated_by_id ON clinical_note (updated_by_id);

--changeset his:003-clinical-note-symptom
CREATE TABLE clinical_note_symptom (
    note_id uuid         NOT NULL,
    symptom varchar(200) NOT NULL,
    CONSTRAINT pk_clinical_note_symptom PRIMARY KEY (note_id, symptom),
    CONSTRAINT fk_clinical_note_symptom_note FOREIGN KEY (note_id) REFERENCES clinical_note (id) ON DELETE CASCADE
);

--changeset his:003-admission-discharge-note-fk
ALTER TABLE admission
    ADD CONSTRAINT fk_admission_discharge_summary_note
    FOREIGN KEY (discharge_summary_note_id) REFERENCES clinical_note (id);

--changeset his:003-diagnosis
CREATE TABLE diagnosis (
    id              uuid         NOT NULL,
    patient_id      uuid         NOT NULL,
    encounter_id    uuid,
    code_system     varchar(10)  NOT NULL,
    code_value      varchar(30)  NOT NULL,
    code_display    varchar(500) NOT NULL,
    type            varchar(10)  NOT NULL,
    status          varchar(10)  NOT NULL,
    diagnosed_at    timestamptz  NOT NULL,
    diagnosed_by_id uuid         NOT NULL,
    notes           text,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by_id   uuid,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by_id   uuid,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_diagnosis PRIMARY KEY (id),
    CONSTRAINT fk_diagnosis_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_diagnosis_encounter FOREIGN KEY (encounter_id) REFERENCES encounter (id),
    CONSTRAINT fk_diagnosis_diagnosed_by FOREIGN KEY (diagnosed_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_diagnosis_created_by FOREIGN KEY (created_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_diagnosis_updated_by FOREIGN KEY (updated_by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_diagnosis_code_system CHECK (code_system IN ('ICD-10', 'LOINC', 'ATC', 'ICD-9-PL', 'local')),
    CONSTRAINT ck_diagnosis_type CHECK (type IN ('primary', 'secondary', 'chronic')),
    CONSTRAINT ck_diagnosis_status CHECK (status IN ('active', 'resolved'))
);
CREATE INDEX ix_diagnosis_patient_id ON diagnosis (patient_id, diagnosed_at DESC);
CREATE INDEX ix_diagnosis_encounter_id ON diagnosis (encounter_id);
CREATE INDEX ix_diagnosis_diagnosed_by_id ON diagnosis (diagnosed_by_id);
CREATE INDEX ix_diagnosis_code ON diagnosis (code_system, code_value);
CREATE INDEX ix_diagnosis_created_by_id ON diagnosis (created_by_id);
CREATE INDEX ix_diagnosis_updated_by_id ON diagnosis (updated_by_id);

--changeset his:003-episode-diagnosis
CREATE TABLE episode_diagnosis (
    episode_id   uuid NOT NULL,
    diagnosis_id uuid NOT NULL,
    CONSTRAINT pk_episode_diagnosis PRIMARY KEY (episode_id, diagnosis_id),
    CONSTRAINT fk_episode_diagnosis_episode FOREIGN KEY (episode_id) REFERENCES treatment_episode (id) ON DELETE CASCADE,
    CONSTRAINT fk_episode_diagnosis_diagnosis FOREIGN KEY (diagnosis_id) REFERENCES diagnosis (id) ON DELETE CASCADE
);
CREATE INDEX ix_episode_diagnosis_diagnosis_id ON episode_diagnosis (diagnosis_id);

--changeset his:003-allergy
CREATE TABLE allergy (
    id             uuid         NOT NULL,
    patient_id     uuid         NOT NULL,
    substance      varchar(200) NOT NULL,
    category       varchar(15)  NOT NULL,
    reaction       varchar(500) NOT NULL,
    severity       varchar(20)  NOT NULL,
    status         varchar(10)  NOT NULL,
    recorded_at    timestamptz  NOT NULL,
    recorded_by_id uuid,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    created_by_id  uuid,
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by_id  uuid,
    version        bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_allergy PRIMARY KEY (id),
    CONSTRAINT fk_allergy_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_allergy_recorded_by FOREIGN KEY (recorded_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_allergy_created_by FOREIGN KEY (created_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_allergy_updated_by FOREIGN KEY (updated_by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_allergy_category CHECK (category IN ('drug', 'food', 'environment', 'other')),
    CONSTRAINT ck_allergy_severity CHECK (severity IN ('mild', 'moderate', 'severe', 'life_threatening')),
    CONSTRAINT ck_allergy_status CHECK (status IN ('active', 'inactive'))
);
CREATE INDEX ix_allergy_patient_id ON allergy (patient_id);
CREATE INDEX ix_allergy_recorded_by_id ON allergy (recorded_by_id);
CREATE INDEX ix_allergy_created_by_id ON allergy (created_by_id);
CREATE INDEX ix_allergy_updated_by_id ON allergy (updated_by_id);

--changeset his:003-allergy-atc-code
CREATE TABLE allergy_atc_code (
    allergy_id uuid        NOT NULL,
    atc_code   varchar(10) NOT NULL,
    CONSTRAINT pk_allergy_atc_code PRIMARY KEY (allergy_id, atc_code),
    CONSTRAINT fk_allergy_atc_code_allergy FOREIGN KEY (allergy_id) REFERENCES allergy (id) ON DELETE CASCADE
);
CREATE INDEX ix_allergy_atc_code_atc_code ON allergy_atc_code (atc_code);

--changeset his:003-contraindication
CREATE TABLE contraindication (
    id          uuid        NOT NULL,
    patient_id  uuid        NOT NULL,
    description text        NOT NULL,
    reason      text        NOT NULL,
    recorded_at timestamptz NOT NULL,
    CONSTRAINT pk_contraindication PRIMARY KEY (id),
    CONSTRAINT fk_contraindication_patient FOREIGN KEY (patient_id) REFERENCES patient (id)
);
CREATE INDEX ix_contraindication_patient_id ON contraindication (patient_id);

--changeset his:003-treatment
CREATE TABLE treatment (
    id              uuid         NOT NULL,
    patient_id      uuid         NOT NULL,
    encounter_id    uuid,
    name            varchar(200) NOT NULL,
    type            varchar(20)  NOT NULL,
    start_at        timestamptz  NOT NULL,
    end_at          timestamptz,
    status          varchar(15)  NOT NULL,
    description     text         NOT NULL,
    practitioner_id uuid         NOT NULL,
    CONSTRAINT pk_treatment PRIMARY KEY (id),
    CONSTRAINT fk_treatment_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_treatment_encounter FOREIGN KEY (encounter_id) REFERENCES encounter (id),
    CONSTRAINT fk_treatment_practitioner FOREIGN KEY (practitioner_id) REFERENCES staff_member (id),
    CONSTRAINT ck_treatment_type CHECK (type IN
        ('pharmacotherapy', 'procedure', 'surgery', 'rehabilitation', 'other')),
    CONSTRAINT ck_treatment_status CHECK (status IN ('ongoing', 'completed', 'discontinued')),
    CONSTRAINT ck_treatment_period CHECK (end_at IS NULL OR end_at >= start_at)
);
CREATE INDEX ix_treatment_patient_id ON treatment (patient_id, start_at DESC);
CREATE INDEX ix_treatment_encounter_id ON treatment (encounter_id);
CREATE INDEX ix_treatment_practitioner_id ON treatment (practitioner_id);

--changeset his:003-icd10-code
CREATE TABLE icd10_code (
    code    varchar(10)  NOT NULL,
    display varchar(500) NOT NULL,
    CONSTRAINT pk_icd10_code PRIMARY KEY (code)
);
CREATE INDEX ix_icd10_code_display ON icd10_code (lower(display));
