--liquibase formatted sql

--changeset his:006-imaging-order
CREATE TABLE imaging_order (
    id                        uuid          NOT NULL,
    patient_id                uuid          NOT NULL,
    encounter_id              uuid,
    exam_code                 varchar(30)   NOT NULL,
    exam_name                 varchar(200)  NOT NULL,
    modality                  varchar(15)   NOT NULL,
    body_region               varchar(100)  NOT NULL,
    laterality                varchar(10)   NOT NULL,
    contrast                  boolean       NOT NULL,
    clinical_indication       text          NOT NULL,
    clinical_question         text,
    diagnosis_code_system     varchar(10),
    diagnosis_code_value      varchar(30),
    diagnosis_code_display    varchar(500),
    urgency                   varchar(10)   NOT NULL,
    safety_pregnancy          varchar(10)   NOT NULL,
    safety_pacemaker_or_implant boolean     NOT NULL,
    safety_metal_fragments    boolean       NOT NULL,
    safety_contrast_allergy   boolean       NOT NULL,
    safety_creatinine         numeric(6,2),
    safety_egfr               numeric(6,1),
    safety_claustrophobia     boolean       NOT NULL,
    safety_confirmed          boolean       NOT NULL,
    slot_id                   uuid,
    scheduled_at              timestamptz,
    ordered_by_id             uuid          NOT NULL,
    ordered_at                timestamptz   NOT NULL DEFAULT now(),
    status                    varchar(20)   NOT NULL DEFAULT 'ordered',
    created_at                timestamptz   NOT NULL DEFAULT now(),
    created_by_id             uuid,
    updated_at                timestamptz   NOT NULL DEFAULT now(),
    updated_by_id             uuid,
    version                   bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_imaging_order PRIMARY KEY (id),
    CONSTRAINT fk_imaging_order_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_imaging_order_encounter FOREIGN KEY (encounter_id) REFERENCES encounter (id),
    CONSTRAINT fk_imaging_order_exam FOREIGN KEY (exam_code) REFERENCES imaging_exam (code),
    CONSTRAINT fk_imaging_order_slot FOREIGN KEY (slot_id) REFERENCES schedule_slot (id),
    CONSTRAINT fk_imaging_order_ordered_by FOREIGN KEY (ordered_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_imaging_order_created_by FOREIGN KEY (created_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_imaging_order_updated_by FOREIGN KEY (updated_by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_imaging_order_modality CHECK (modality IN
        ('USG', 'RTG', 'CT', 'MRI', 'MMG', 'ENDOSCOPY', 'COLONOSCOPY', 'ANGIOGRAPHY')),
    CONSTRAINT ck_imaging_order_laterality CHECK (laterality IN ('left', 'right', 'bilateral', 'na')),
    CONSTRAINT ck_imaging_order_urgency CHECK (urgency IN ('routine', 'urgent', 'stat')),
    CONSTRAINT ck_imaging_order_status CHECK (status IN
        ('ordered', 'scheduled', 'specimen_collected', 'in_progress', 'completed', 'cancelled')),
    CONSTRAINT ck_imaging_order_safety_pregnancy CHECK (safety_pregnancy IN ('no', 'yes', 'unknown', 'na')),
    CONSTRAINT ck_imaging_order_diagnosis_code_system CHECK (diagnosis_code_system IS NULL OR diagnosis_code_system IN
        ('ICD-10', 'LOINC', 'ATC', 'ICD-9-PL', 'local')),
    CONSTRAINT ck_imaging_order_diagnosis_code_complete CHECK (
        (diagnosis_code_system IS NULL) = (diagnosis_code_value IS NULL)
        AND (diagnosis_code_system IS NULL) = (diagnosis_code_display IS NULL))
);
CREATE INDEX ix_imaging_order_patient_id ON imaging_order (patient_id, ordered_at DESC);
CREATE INDEX ix_imaging_order_encounter_id ON imaging_order (encounter_id);
CREATE INDEX ix_imaging_order_exam_code ON imaging_order (exam_code);
CREATE INDEX ix_imaging_order_ordered_by_id ON imaging_order (ordered_by_id);
CREATE INDEX ix_imaging_order_status ON imaging_order (status, ordered_at DESC);
CREATE INDEX ix_imaging_order_created_by_id ON imaging_order (created_by_id);
CREATE INDEX ix_imaging_order_updated_by_id ON imaging_order (updated_by_id);
CREATE UNIQUE INDEX uq_imaging_order_slot_id ON imaging_order (slot_id)
    WHERE slot_id IS NOT NULL AND status <> 'cancelled';

--changeset his:006-imaging-order-status-change
CREATE TABLE imaging_order_status_change (
    id       uuid        NOT NULL,
    order_id uuid        NOT NULL,
    status   varchar(20) NOT NULL,
    at       timestamptz NOT NULL,
    by_id    uuid,
    note     text,
    CONSTRAINT pk_imaging_order_status_change PRIMARY KEY (id),
    CONSTRAINT fk_imaging_order_status_change_order FOREIGN KEY (order_id) REFERENCES imaging_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_imaging_order_status_change_by FOREIGN KEY (by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_imaging_order_status_change_status CHECK (status IN
        ('ordered', 'scheduled', 'specimen_collected', 'in_progress', 'completed', 'cancelled'))
);
CREATE INDEX ix_imaging_order_status_change_order_id ON imaging_order_status_change (order_id, at);
CREATE INDEX ix_imaging_order_status_change_by_id ON imaging_order_status_change (by_id);

--changeset his:006-imaging-result
CREATE TABLE imaging_result (
    id               uuid         NOT NULL,
    patient_id       uuid         NOT NULL,
    order_id         uuid,
    modality         varchar(15)  NOT NULL,
    exam_name        varchar(200) NOT NULL,
    body_region      varchar(100) NOT NULL,
    performed_at     timestamptz  NOT NULL,
    reported_at      timestamptz  NOT NULL,
    radiologist_name varchar(200) NOT NULL,
    radiologist_id   uuid,
    technique        text,
    findings         text         NOT NULL,
    conclusion       text         NOT NULL,
    status           varchar(15)  NOT NULL,
    image_count      integer      NOT NULL,
    critical         boolean      NOT NULL,
    reviewed_at      timestamptz,
    reviewed_by_id   uuid,
    CONSTRAINT pk_imaging_result PRIMARY KEY (id),
    CONSTRAINT fk_imaging_result_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_imaging_result_order FOREIGN KEY (order_id) REFERENCES imaging_order (id),
    CONSTRAINT fk_imaging_result_radiologist FOREIGN KEY (radiologist_id) REFERENCES staff_member (id),
    CONSTRAINT fk_imaging_result_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_imaging_result_modality CHECK (modality IN
        ('USG', 'RTG', 'CT', 'MRI', 'MMG', 'ENDOSCOPY', 'COLONOSCOPY', 'ANGIOGRAPHY')),
    CONSTRAINT ck_imaging_result_status CHECK (status IN ('preliminary', 'final')),
    CONSTRAINT ck_imaging_result_image_count CHECK (image_count >= 0),
    CONSTRAINT ck_imaging_result_reviewed_complete CHECK ((reviewed_at IS NULL) = (reviewed_by_id IS NULL))
);
CREATE INDEX ix_imaging_result_patient_id ON imaging_result (patient_id, reported_at DESC);
CREATE INDEX ix_imaging_result_order_id ON imaging_result (order_id);
CREATE INDEX ix_imaging_result_radiologist_id ON imaging_result (radiologist_id);
CREATE INDEX ix_imaging_result_reviewed_by_id ON imaging_result (reviewed_by_id);
CREATE INDEX ix_imaging_result_unreviewed ON imaging_result (reported_at DESC) WHERE reviewed_at IS NULL;
