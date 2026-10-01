--liquibase formatted sql

--changeset his:005-lab-order
CREATE TABLE lab_order (
    id                     uuid         NOT NULL,
    patient_id             uuid         NOT NULL,
    encounter_id           uuid,
    ordered_by_id          uuid         NOT NULL,
    ordered_at             timestamptz  NOT NULL DEFAULT now(),
    urgency                varchar(10)  NOT NULL,
    fasting                boolean      NOT NULL,
    planned_collection_at  timestamptz  NOT NULL,
    diagnosis_code_system  varchar(10),
    diagnosis_code_value   varchar(30),
    diagnosis_code_display varchar(500),
    clinical_info          text         NOT NULL,
    notes                  text,
    status                 varchar(20)  NOT NULL DEFAULT 'ordered',
    created_at             timestamptz  NOT NULL DEFAULT now(),
    created_by_id          uuid,
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    updated_by_id          uuid,
    version                bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_lab_order PRIMARY KEY (id),
    CONSTRAINT fk_lab_order_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_lab_order_encounter FOREIGN KEY (encounter_id) REFERENCES encounter (id),
    CONSTRAINT fk_lab_order_ordered_by FOREIGN KEY (ordered_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_lab_order_created_by FOREIGN KEY (created_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_lab_order_updated_by FOREIGN KEY (updated_by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_lab_order_urgency CHECK (urgency IN ('routine', 'urgent', 'stat')),
    CONSTRAINT ck_lab_order_status CHECK (status IN
        ('ordered', 'scheduled', 'specimen_collected', 'in_progress', 'completed', 'cancelled')),
    CONSTRAINT ck_lab_order_diagnosis_code_system CHECK (diagnosis_code_system IS NULL OR diagnosis_code_system IN
        ('ICD-10', 'LOINC', 'ATC', 'ICD-9-PL', 'local')),
    CONSTRAINT ck_lab_order_diagnosis_code_complete CHECK (
        (diagnosis_code_system IS NULL) = (diagnosis_code_value IS NULL)
        AND (diagnosis_code_system IS NULL) = (diagnosis_code_display IS NULL))
);
CREATE INDEX ix_lab_order_patient_id ON lab_order (patient_id, ordered_at DESC);
CREATE INDEX ix_lab_order_encounter_id ON lab_order (encounter_id);
CREATE INDEX ix_lab_order_ordered_by_id ON lab_order (ordered_by_id);
CREATE INDEX ix_lab_order_status ON lab_order (status, ordered_at DESC);
CREATE INDEX ix_lab_order_created_by_id ON lab_order (created_by_id);
CREATE INDEX ix_lab_order_updated_by_id ON lab_order (updated_by_id);

--changeset his:005-lab-order-item
CREATE TABLE lab_order_item (
    id            uuid         NOT NULL,
    order_id      uuid         NOT NULL,
    test_code     varchar(30)  NOT NULL,
    test_name     varchar(200) NOT NULL,
    specimen_id   uuid,
    specimen_type varchar(10)  NOT NULL,
    CONSTRAINT pk_lab_order_item PRIMARY KEY (id),
    CONSTRAINT fk_lab_order_item_order FOREIGN KEY (order_id) REFERENCES lab_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_order_item_test FOREIGN KEY (test_code) REFERENCES lab_test (code),
    CONSTRAINT ck_lab_order_item_specimen_type CHECK (specimen_type IN
        ('blood', 'serum', 'urine', 'stool', 'swab', 'csf', 'tissue'))
);
CREATE INDEX ix_lab_order_item_order_id ON lab_order_item (order_id);
CREATE INDEX ix_lab_order_item_test_code ON lab_order_item (test_code);

--changeset his:005-lab-order-status-change
CREATE TABLE lab_order_status_change (
    id       uuid        NOT NULL,
    order_id uuid        NOT NULL,
    status   varchar(20) NOT NULL,
    at       timestamptz NOT NULL,
    by_id    uuid,
    note     text,
    CONSTRAINT pk_lab_order_status_change PRIMARY KEY (id),
    CONSTRAINT fk_lab_order_status_change_order FOREIGN KEY (order_id) REFERENCES lab_order (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_order_status_change_by FOREIGN KEY (by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_lab_order_status_change_status CHECK (status IN
        ('ordered', 'scheduled', 'specimen_collected', 'in_progress', 'completed', 'cancelled'))
);
CREATE INDEX ix_lab_order_status_change_order_id ON lab_order_status_change (order_id, at);
CREATE INDEX ix_lab_order_status_change_by_id ON lab_order_status_change (by_id);

--changeset his:005-lab-result
CREATE TABLE lab_result (
    id               uuid         NOT NULL,
    patient_id       uuid         NOT NULL,
    order_id         uuid,
    order_item_id    uuid,
    test_code        varchar(30)  NOT NULL,
    test_name        varchar(200) NOT NULL,
    category         varchar(20)  NOT NULL,
    collected_at     timestamptz  NOT NULL,
    resulted_at      timestamptz  NOT NULL,
    status           varchar(15)  NOT NULL,
    performer_name   varchar(200) NOT NULL,
    comment          text,
    reviewed_at      timestamptz,
    reviewed_by_id   uuid,
    CONSTRAINT pk_lab_result PRIMARY KEY (id),
    CONSTRAINT fk_lab_result_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_lab_result_order FOREIGN KEY (order_id) REFERENCES lab_order (id),
    CONSTRAINT fk_lab_result_order_item FOREIGN KEY (order_item_id) REFERENCES lab_order_item (id),
    CONSTRAINT fk_lab_result_test FOREIGN KEY (test_code) REFERENCES lab_test (code),
    CONSTRAINT fk_lab_result_reviewed_by FOREIGN KEY (reviewed_by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_lab_result_category CHECK (category IN
        ('hematology', 'biochemistry', 'coagulation', 'immunology', 'urinalysis', 'microbiology', 'pathology')),
    CONSTRAINT ck_lab_result_status CHECK (status IN ('preliminary', 'final', 'corrected')),
    CONSTRAINT ck_lab_result_reviewed_complete CHECK ((reviewed_at IS NULL) = (reviewed_by_id IS NULL))
);
CREATE INDEX ix_lab_result_patient_id ON lab_result (patient_id, collected_at DESC);
CREATE INDEX ix_lab_result_order_id ON lab_result (order_id);
CREATE INDEX ix_lab_result_order_item_id ON lab_result (order_item_id);
CREATE INDEX ix_lab_result_test_code ON lab_result (test_code);
CREATE INDEX ix_lab_result_reviewed_by_id ON lab_result (reviewed_by_id);
CREATE INDEX ix_lab_result_unreviewed ON lab_result (resulted_at DESC) WHERE reviewed_at IS NULL;

--changeset his:005-lab-observation
CREATE TABLE lab_observation (
    id            uuid          NOT NULL,
    result_id     uuid          NOT NULL,
    analyte_code  varchar(30)   NOT NULL,
    analyte_name  varchar(200)  NOT NULL,
    value_numeric numeric(14,4),
    value_text    varchar(500),
    unit          varchar(30)   NOT NULL,
    ref_low       numeric(14,4),
    ref_high      numeric(14,4),
    ref_text      varchar(200),
    flag          varchar(2)    NOT NULL,
    CONSTRAINT pk_lab_observation PRIMARY KEY (id),
    CONSTRAINT fk_lab_observation_result FOREIGN KEY (result_id) REFERENCES lab_result (id) ON DELETE CASCADE,
    CONSTRAINT ck_lab_observation_value CHECK ((value_numeric IS NULL) <> (value_text IS NULL)),
    CONSTRAINT ck_lab_observation_flag CHECK (flag IN ('N', 'L', 'H', 'LL', 'HH', 'A'))
);
CREATE INDEX ix_lab_observation_result_id ON lab_observation (result_id);
CREATE INDEX ix_lab_observation_analyte_code ON lab_observation (analyte_code);
CREATE INDEX ix_lab_observation_critical ON lab_observation (result_id) WHERE flag IN ('LL', 'HH');
