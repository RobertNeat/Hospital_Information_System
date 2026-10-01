--liquibase formatted sql

--changeset his:002-mrn-sequence
CREATE SEQUENCE patient_mrn_seq START WITH 1 INCREMENT BY 1 NO CYCLE;

--changeset his:002-patient
CREATE TABLE patient (
    id                          uuid         NOT NULL,
    mrn                         varchar(30)  NOT NULL,
    pesel                       varchar(11),
    no_pesel_reason             varchar(30),
    identity_doc_type           varchar(20),
    identity_doc_number         varchar(50),
    first_name                  varchar(100) NOT NULL,
    last_name                   varchar(100) NOT NULL,
    second_name                 varchar(100),
    birth_date                  date         NOT NULL,
    gender                      varchar(10)  NOT NULL,
    phone                       varchar(30),
    email                       varchar(200),
    address_street              varchar(200) NOT NULL,
    address_building_number     varchar(20)  NOT NULL,
    address_apartment_number    varchar(20),
    address_postal_code         varchar(20)  NOT NULL,
    address_city                varchar(100) NOT NULL,
    address_country             varchar(100) NOT NULL,
    emergency_contact_full_name         varchar(200),
    emergency_contact_relation          varchar(100),
    emergency_contact_phone             varchar(30),
    emergency_contact_is_legal_guardian boolean,
    insurance_status            varchar(10)  NOT NULL,
    insurance_nfz_branch        varchar(100) NOT NULL,
    insurance_payer             varchar(10)  NOT NULL,
    insurance_ewus_verified_at  timestamptz,
    blood_type                  varchar(3),
    status                      varchar(15)  NOT NULL DEFAULT 'registered',
    created_at                  timestamptz  NOT NULL DEFAULT now(),
    created_by_id               uuid,
    updated_at                  timestamptz  NOT NULL DEFAULT now(),
    updated_by_id               uuid,
    version                     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_patient PRIMARY KEY (id),
    CONSTRAINT uq_patient_mrn UNIQUE (mrn),
    CONSTRAINT fk_patient_created_by FOREIGN KEY (created_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_patient_updated_by FOREIGN KEY (updated_by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_patient_pesel CHECK (pesel IS NULL OR pesel ~ '^[0-9]{11}$'),
    CONSTRAINT ck_patient_no_pesel_reason CHECK (no_pesel_reason IS NULL OR no_pesel_reason IN
        ('foreigner', 'newborn', 'unknown_identity')),
    CONSTRAINT ck_patient_pesel_or_reason CHECK (pesel IS NOT NULL OR no_pesel_reason IS NOT NULL),
    CONSTRAINT ck_patient_identity_doc_type CHECK (identity_doc_type IS NULL OR identity_doc_type IN
        ('id_card', 'passport', 'other')),
    CONSTRAINT ck_patient_identity_doc_complete CHECK
        ((identity_doc_type IS NULL) = (identity_doc_number IS NULL)),
    CONSTRAINT ck_patient_gender CHECK (gender IN ('female', 'male', 'other', 'unknown')),
    CONSTRAINT ck_patient_emergency_contact_complete CHECK (
        (emergency_contact_full_name IS NULL) = (emergency_contact_relation IS NULL)
        AND (emergency_contact_full_name IS NULL) = (emergency_contact_phone IS NULL)
        AND (emergency_contact_full_name IS NULL) = (emergency_contact_is_legal_guardian IS NULL)),
    CONSTRAINT ck_patient_insurance_status CHECK (insurance_status IN ('active', 'inactive', 'unknown')),
    CONSTRAINT ck_patient_insurance_payer CHECK (insurance_payer IN ('NFZ', 'private', 'none')),
    CONSTRAINT ck_patient_blood_type CHECK (blood_type IS NULL OR blood_type IN
        ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', '0+', '0-')),
    CONSTRAINT ck_patient_status CHECK (status IN ('registered', 'admitted', 'outpatient', 'discharged'))
);
CREATE UNIQUE INDEX uq_patient_pesel ON patient (pesel) WHERE pesel IS NOT NULL;
CREATE INDEX ix_patient_last_name ON patient (lower(last_name), lower(first_name));
CREATE INDEX ix_patient_birth_date ON patient (birth_date);
CREATE INDEX ix_patient_status ON patient (status);
CREATE INDEX ix_patient_created_by_id ON patient (created_by_id);
CREATE INDEX ix_patient_updated_by_id ON patient (updated_by_id);


--changeset his:002-patient-flag
CREATE TABLE patient_flag (
    patient_id uuid        NOT NULL,
    flag       varchar(20) NOT NULL,
    CONSTRAINT pk_patient_flag PRIMARY KEY (patient_id, flag),
    CONSTRAINT fk_patient_flag_patient FOREIGN KEY (patient_id) REFERENCES patient (id) ON DELETE CASCADE,
    CONSTRAINT ck_patient_flag_flag CHECK (flag IN ('isolation', 'fall_risk', 'dnr', 'infection_risk', 'vip'))
);

--changeset his:002-treatment-episode
CREATE TABLE treatment_episode (
    id         uuid         NOT NULL,
    patient_id uuid         NOT NULL,
    title      varchar(200) NOT NULL,
    start_at   timestamptz  NOT NULL,
    end_at     timestamptz,
    status     varchar(10)  NOT NULL,
    CONSTRAINT pk_treatment_episode PRIMARY KEY (id),
    CONSTRAINT fk_treatment_episode_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT ck_treatment_episode_status CHECK (status IN ('active', 'closed')),
    CONSTRAINT ck_treatment_episode_period CHECK (end_at IS NULL OR end_at >= start_at)
);
CREATE INDEX ix_treatment_episode_patient_id ON treatment_episode (patient_id, start_at DESC);

--changeset his:002-encounter
CREATE TABLE encounter (
    id              uuid         NOT NULL,
    patient_id      uuid         NOT NULL,
    type            varchar(20)  NOT NULL,
    status          varchar(15)  NOT NULL,
    start_at        timestamptz  NOT NULL,
    end_at          timestamptz,
    ward_id         uuid,
    practitioner_id uuid         NOT NULL,
    reason          text         NOT NULL,
    summary         text,
    episode_id      uuid,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_encounter PRIMARY KEY (id),
    CONSTRAINT fk_encounter_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_encounter_ward FOREIGN KEY (ward_id) REFERENCES ward (id),
    CONSTRAINT fk_encounter_practitioner FOREIGN KEY (practitioner_id) REFERENCES staff_member (id),
    CONSTRAINT fk_encounter_episode FOREIGN KEY (episode_id) REFERENCES treatment_episode (id),
    CONSTRAINT ck_encounter_type CHECK (type IN
        ('visit', 'consultation', 'hospitalization', 'emergency', 'teleconsultation')),
    CONSTRAINT ck_encounter_status CHECK (status IN ('planned', 'in_progress', 'finished', 'cancelled')),
    CONSTRAINT ck_encounter_period CHECK (end_at IS NULL OR end_at >= start_at)
);
CREATE INDEX ix_encounter_patient_id ON encounter (patient_id, start_at DESC);
CREATE INDEX ix_encounter_ward_id ON encounter (ward_id);
CREATE INDEX ix_encounter_practitioner_id ON encounter (practitioner_id);
CREATE INDEX ix_encounter_episode_id ON encounter (episode_id);

--changeset his:002-admission
CREATE TABLE admission (
    id                        uuid         NOT NULL,
    patient_id                uuid         NOT NULL,
    encounter_id              uuid,
    status                    varchar(10)  NOT NULL DEFAULT 'active',
    admission_type            varchar(10)  NOT NULL,
    admitted_at               timestamptz  NOT NULL,
    ward_id                   uuid         NOT NULL,
    room                      varchar(20),
    bed                       varchar(20),
    attending_physician_id    uuid         NOT NULL,
    triage_level              varchar(10),
    reason                    text         NOT NULL,
    referral_number           varchar(50),
    discharged_at             timestamptz,
    discharge_disposition     varchar(20),
    discharge_summary_note_id uuid,
    version                   bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_admission PRIMARY KEY (id),
    CONSTRAINT fk_admission_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_admission_encounter FOREIGN KEY (encounter_id) REFERENCES encounter (id),
    CONSTRAINT uq_admission_encounter_id UNIQUE (encounter_id),
    CONSTRAINT fk_admission_ward FOREIGN KEY (ward_id) REFERENCES ward (id),
    CONSTRAINT fk_admission_attending_physician FOREIGN KEY (attending_physician_id) REFERENCES staff_member (id),
    CONSTRAINT ck_admission_status CHECK (status IN ('active', 'discharged', 'cancelled')),
    CONSTRAINT ck_admission_type CHECK (admission_type IN ('planned', 'emergency', 'transfer', 'outpatient')),
    CONSTRAINT ck_admission_triage CHECK (triage_level IS NULL OR triage_level IN
        ('red', 'orange', 'yellow', 'green', 'blue')),
    CONSTRAINT ck_admission_disposition CHECK (discharge_disposition IS NULL OR discharge_disposition IN
        ('home', 'transfer', 'deceased', 'against_advice', 'other')),
    CONSTRAINT ck_admission_discharged_at CHECK (status <> 'discharged' OR discharged_at IS NOT NULL),
    CONSTRAINT ck_admission_period CHECK (discharged_at IS NULL OR discharged_at >= admitted_at)
);
CREATE UNIQUE INDEX uq_admission_active_per_patient ON admission (patient_id) WHERE status = 'active';
CREATE INDEX ix_admission_patient_id ON admission (patient_id, admitted_at DESC);
CREATE INDEX ix_admission_ward_active ON admission (ward_id) WHERE status = 'active';
CREATE INDEX ix_admission_attending_physician_id ON admission (attending_physician_id);
CREATE INDEX ix_admission_discharge_summary_note_id ON admission (discharge_summary_note_id)
    WHERE discharge_summary_note_id IS NOT NULL;
