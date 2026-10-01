--liquibase formatted sql

--changeset his:007-prescription
CREATE TABLE prescription (
    id            uuid         NOT NULL,
    patient_id    uuid         NOT NULL,
    encounter_id  uuid,
    prescriber_id uuid         NOT NULL,
    issued_at     timestamptz  NOT NULL DEFAULT now(),
    valid_from    date         NOT NULL,
    valid_until   date         NOT NULL,
    kind          varchar(20)  NOT NULL,
    status        varchar(25)  NOT NULL DEFAULT 'issued',
    access_code   char(4)      NOT NULL,
    erx_key       char(44)     NOT NULL,
    notes         text,
    cancelled_at  timestamptz,
    cancel_reason text,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by_id uuid,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by_id uuid,
    version       bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_prescription PRIMARY KEY (id),
    CONSTRAINT fk_prescription_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_prescription_encounter FOREIGN KEY (encounter_id) REFERENCES encounter (id),
    CONSTRAINT fk_prescription_prescriber FOREIGN KEY (prescriber_id) REFERENCES staff_member (id),
    CONSTRAINT fk_prescription_created_by FOREIGN KEY (created_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_prescription_updated_by FOREIGN KEY (updated_by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_prescription_kind CHECK (kind IN ('e_prescription', 'hospital_order')),
    CONSTRAINT ck_prescription_status CHECK (status IN
        ('issued', 'partially_dispensed', 'dispensed', 'cancelled', 'expired')),
    CONSTRAINT ck_prescription_access_code CHECK (access_code ~ '^[0-9]{4}$'),
    CONSTRAINT ck_prescription_validity CHECK (valid_until >= valid_from)
);
CREATE INDEX ix_prescription_patient_id ON prescription (patient_id, issued_at DESC);
CREATE INDEX ix_prescription_encounter_id ON prescription (encounter_id);
CREATE INDEX ix_prescription_prescriber_id ON prescription (prescriber_id);
CREATE INDEX ix_prescription_status ON prescription (status, valid_until);
CREATE INDEX ix_prescription_created_by_id ON prescription (created_by_id);
CREATE INDEX ix_prescription_updated_by_id ON prescription (updated_by_id);

--changeset his:007-prescription-item
CREATE TABLE prescription_item (
    id                  uuid          NOT NULL,
    prescription_id     uuid          NOT NULL,
    drug_id             uuid          NOT NULL,
    drug_name           varchar(200)  NOT NULL,
    active_substance    varchar(200)  NOT NULL,
    strength            varchar(50)   NOT NULL,
    form                varchar(15)   NOT NULL,
    dosage_dose         numeric(10,3) NOT NULL,
    dosage_dose_unit    varchar(30)   NOT NULL,
    dosage_route        varchar(15)   NOT NULL,
    dosage_frequency    varchar(5)    NOT NULL,
    dosage_duration_days integer      NOT NULL,
    dosage_as_needed    boolean       NOT NULL,
    dosage_max_per_day  integer,
    dosage_instructions text,
    quantity_packages   integer       NOT NULL,
    reimbursement       varchar(10)   NOT NULL,
    substitution_allowed boolean      NOT NULL,
    CONSTRAINT pk_prescription_item PRIMARY KEY (id),
    CONSTRAINT fk_prescription_item_prescription FOREIGN KEY (prescription_id) REFERENCES prescription (id) ON DELETE CASCADE,
    CONSTRAINT fk_prescription_item_drug FOREIGN KEY (drug_id) REFERENCES drug (id),
    CONSTRAINT ck_prescription_item_form CHECK (form IN
        ('tablet', 'capsule', 'injection', 'syrup', 'drops', 'ointment', 'inhaler', 'suppository', 'patch')),
    CONSTRAINT ck_prescription_item_route CHECK (dosage_route IN
        ('oral', 'sublingual', 'iv', 'im', 'sc', 'topical', 'inhalation', 'rectal', 'transdermal')),
    CONSTRAINT ck_prescription_item_frequency CHECK (dosage_frequency IN
        ('QD', 'BID', 'TID', 'QID', 'Q4H', 'Q6H', 'Q8H', 'Q12H', 'QW', 'PRN')),
    CONSTRAINT ck_prescription_item_reimbursement CHECK (reimbursement IN
        ('100%', '50%', '30%', 'R', 'B', 'none')),
    CONSTRAINT ck_prescription_item_dose CHECK (dosage_dose > 0),
    CONSTRAINT ck_prescription_item_duration CHECK (dosage_duration_days > 0),
    CONSTRAINT ck_prescription_item_quantity CHECK (quantity_packages > 0)
);
CREATE INDEX ix_prescription_item_prescription_id ON prescription_item (prescription_id);
CREATE INDEX ix_prescription_item_drug_id ON prescription_item (drug_id);

--changeset his:007-prescription-item-time-of-day
CREATE TABLE prescription_item_time_of_day (
    item_id     uuid        NOT NULL,
    time_of_day varchar(10) NOT NULL,
    CONSTRAINT pk_prescription_item_time_of_day PRIMARY KEY (item_id, time_of_day),
    CONSTRAINT fk_prescription_item_time_of_day_item FOREIGN KEY (item_id) REFERENCES prescription_item (id) ON DELETE CASCADE,
    CONSTRAINT ck_prescription_item_time_of_day_value CHECK (time_of_day IN ('morning', 'noon', 'evening', 'night'))
);
