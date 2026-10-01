--liquibase formatted sql

--changeset his:004-lab-test
CREATE TABLE lab_test (
    code             varchar(30)  NOT NULL,
    loinc            varchar(20),
    name             varchar(200) NOT NULL,
    category         varchar(20)  NOT NULL,
    default_specimen varchar(10)  NOT NULL,
    turnaround_hours integer      NOT NULL,
    fasting_required boolean      NOT NULL,
    CONSTRAINT pk_lab_test PRIMARY KEY (code),
    CONSTRAINT ck_lab_test_category CHECK (category IN
        ('hematology', 'biochemistry', 'coagulation', 'immunology', 'urinalysis', 'microbiology', 'pathology')),
    CONSTRAINT ck_lab_test_default_specimen CHECK (default_specimen IN
        ('blood', 'serum', 'urine', 'stool', 'swab', 'csf', 'tissue')),
    CONSTRAINT ck_lab_test_turnaround CHECK (turnaround_hours >= 0)
);
CREATE INDEX ix_lab_test_category ON lab_test (category);
CREATE INDEX ix_lab_test_name ON lab_test (lower(name));

--changeset his:004-lab-test-specimen
CREATE TABLE lab_test_specimen (
    test_code     varchar(30) NOT NULL,
    specimen_type varchar(10) NOT NULL,
    CONSTRAINT pk_lab_test_specimen PRIMARY KEY (test_code, specimen_type),
    CONSTRAINT fk_lab_test_specimen_test FOREIGN KEY (test_code) REFERENCES lab_test (code) ON DELETE CASCADE,
    CONSTRAINT ck_lab_test_specimen_type CHECK (specimen_type IN
        ('blood', 'serum', 'urine', 'stool', 'swab', 'csf', 'tissue'))
);

--changeset his:004-lab-test-default-specimen-fk
ALTER TABLE lab_test
    ADD CONSTRAINT fk_lab_test_default_specimen
    FOREIGN KEY (code, default_specimen) REFERENCES lab_test_specimen (test_code, specimen_type)
    DEFERRABLE INITIALLY DEFERRED;

--changeset his:004-lab-analyte-definition
CREATE TABLE lab_analyte_definition (
    id        uuid          NOT NULL,
    test_code varchar(30)   NOT NULL,
    code      varchar(30)   NOT NULL,
    name      varchar(200)  NOT NULL,
    unit      varchar(30)   NOT NULL,
    low       numeric(14,4),
    high      numeric(14,4),
    CONSTRAINT pk_lab_analyte_definition PRIMARY KEY (id),
    CONSTRAINT fk_lab_analyte_definition_test FOREIGN KEY (test_code) REFERENCES lab_test (code) ON DELETE CASCADE,
    CONSTRAINT uq_lab_analyte_definition_test_code_code UNIQUE (test_code, code),
    CONSTRAINT ck_lab_analyte_definition_range CHECK (low IS NULL OR high IS NULL OR low <= high)
);
CREATE INDEX ix_lab_analyte_definition_code ON lab_analyte_definition (code);

--changeset his:004-lab-panel
CREATE TABLE lab_panel (
    id   uuid         NOT NULL,
    name varchar(200) NOT NULL,
    CONSTRAINT pk_lab_panel PRIMARY KEY (id)
);

--changeset his:004-lab-panel-test
CREATE TABLE lab_panel_test (
    panel_id  uuid        NOT NULL,
    test_code varchar(30) NOT NULL,
    CONSTRAINT pk_lab_panel_test PRIMARY KEY (panel_id, test_code),
    CONSTRAINT fk_lab_panel_test_panel FOREIGN KEY (panel_id) REFERENCES lab_panel (id) ON DELETE CASCADE,
    CONSTRAINT fk_lab_panel_test_test FOREIGN KEY (test_code) REFERENCES lab_test (code)
);
CREATE INDEX ix_lab_panel_test_test_code ON lab_panel_test (test_code);

--changeset his:004-imaging-exam
CREATE TABLE imaging_exam (
    code                varchar(30)  NOT NULL,
    modality            varchar(15)  NOT NULL,
    name                varchar(200) NOT NULL,
    body_region         varchar(100) NOT NULL,
    contrast_possible   boolean      NOT NULL,
    requires_laterality boolean      NOT NULL,
    preparation         text,
    duration_minutes    integer      NOT NULL,
    CONSTRAINT pk_imaging_exam PRIMARY KEY (code),
    CONSTRAINT ck_imaging_exam_modality CHECK (modality IN
        ('USG', 'RTG', 'CT', 'MRI', 'MMG', 'ENDOSCOPY', 'COLONOSCOPY', 'ANGIOGRAPHY')),
    CONSTRAINT ck_imaging_exam_duration CHECK (duration_minutes > 0)
);
CREATE INDEX ix_imaging_exam_modality ON imaging_exam (modality);

--changeset his:004-schedule-slot
CREATE TABLE schedule_slot (
    id        uuid         NOT NULL,
    modality  varchar(15)  NOT NULL,
    start_at  timestamptz  NOT NULL,
    end_at    timestamptz  NOT NULL,
    room      varchar(50)  NOT NULL,
    available boolean      NOT NULL DEFAULT true,
    CONSTRAINT pk_schedule_slot PRIMARY KEY (id),
    CONSTRAINT ck_schedule_slot_modality CHECK (modality IN
        ('USG', 'RTG', 'CT', 'MRI', 'MMG', 'ENDOSCOPY', 'COLONOSCOPY', 'ANGIOGRAPHY')),
    CONSTRAINT ck_schedule_slot_period CHECK (end_at > start_at)
);
CREATE INDEX ix_schedule_slot_modality_start ON schedule_slot (modality, start_at);

--changeset his:004-drug
CREATE TABLE drug (
    id                  uuid          NOT NULL,
    name                varchar(200)  NOT NULL,
    active_substance    varchar(200)  NOT NULL,
    atc_code            varchar(10)   NOT NULL,
    form                varchar(15)   NOT NULL,
    strength            varchar(50)   NOT NULL,
    package_size        integer       NOT NULL,
    package_unit        varchar(30)   NOT NULL,
    default_dose_unit   varchar(30)   NOT NULL,
    rx_only             boolean       NOT NULL,
    max_daily_dose_value numeric(12,3),
    max_daily_dose_unit  varchar(30),
    CONSTRAINT pk_drug PRIMARY KEY (id),
    CONSTRAINT ck_drug_form CHECK (form IN
        ('tablet', 'capsule', 'injection', 'syrup', 'drops', 'ointment', 'inhaler', 'suppository', 'patch')),
    CONSTRAINT ck_drug_package_size CHECK (package_size > 0),
    CONSTRAINT ck_drug_max_daily_dose_complete CHECK
        ((max_daily_dose_value IS NULL) = (max_daily_dose_unit IS NULL))
);
CREATE INDEX ix_drug_atc_code ON drug (atc_code);
CREATE INDEX ix_drug_name_lower ON drug (lower(name));
CREATE INDEX ix_drug_active_substance_lower ON drug (lower(active_substance));

--changeset his:004-drug-route
CREATE TABLE drug_route (
    drug_id uuid        NOT NULL,
    route   varchar(15) NOT NULL,
    CONSTRAINT pk_drug_route PRIMARY KEY (drug_id, route),
    CONSTRAINT fk_drug_route_drug FOREIGN KEY (drug_id) REFERENCES drug (id) ON DELETE CASCADE,
    CONSTRAINT ck_drug_route_route CHECK (route IN
        ('oral', 'sublingual', 'iv', 'im', 'sc', 'topical', 'inhalation', 'rectal', 'transdermal'))
);

--changeset his:004-drug-reimbursement-option
CREATE TABLE drug_reimbursement_option (
    drug_id       uuid        NOT NULL,
    reimbursement varchar(10) NOT NULL,
    CONSTRAINT pk_drug_reimbursement_option PRIMARY KEY (drug_id, reimbursement),
    CONSTRAINT fk_drug_reimbursement_option_drug FOREIGN KEY (drug_id) REFERENCES drug (id) ON DELETE CASCADE,
    CONSTRAINT ck_drug_reimbursement_option_value CHECK (reimbursement IN
        ('100%', '50%', '30%', 'R', 'B', 'none'))
);

--changeset his:004-drug-interacts-with-atc
CREATE TABLE drug_interacts_with_atc (
    drug_id  uuid        NOT NULL,
    atc_code varchar(10) NOT NULL,
    CONSTRAINT pk_drug_interacts_with_atc PRIMARY KEY (drug_id, atc_code),
    CONSTRAINT fk_drug_interacts_with_atc_drug FOREIGN KEY (drug_id) REFERENCES drug (id) ON DELETE CASCADE
);
CREATE INDEX ix_drug_interacts_with_atc_atc_code ON drug_interacts_with_atc (atc_code);

--changeset his:004-vital-threshold
CREATE TABLE vital_threshold (
    type          varchar(20)  NOT NULL,
    label         varchar(100) NOT NULL,
    unit          varchar(20)  NOT NULL,
    low           numeric(6,1) NOT NULL,
    high          numeric(6,1) NOT NULL,
    critical_low  numeric(6,1) NOT NULL,
    critical_high numeric(6,1) NOT NULL,
    min_value     numeric(6,1) NOT NULL,
    max_value     numeric(6,1) NOT NULL,
    CONSTRAINT pk_vital_threshold PRIMARY KEY (type),
    CONSTRAINT ck_vital_threshold_type CHECK (type IN
        ('systolic', 'diastolic', 'heartRate', 'temperature', 'spo2', 'respiratoryRate')),
    CONSTRAINT ck_vital_threshold_order CHECK
        (min_value <= critical_low AND critical_low <= low AND low <= high
         AND high <= critical_high AND critical_high <= max_value)
);
