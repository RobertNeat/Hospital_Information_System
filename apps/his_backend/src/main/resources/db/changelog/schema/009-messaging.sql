--liquibase formatted sql

--changeset his:009-message-thread
CREATE TABLE message_thread (
    id              uuid         NOT NULL,
    subject         varchar(200) NOT NULL,
    patient_id      uuid,
    created_by_id   uuid,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    last_message_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_message_thread PRIMARY KEY (id),
    CONSTRAINT fk_message_thread_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_message_thread_created_by FOREIGN KEY (created_by_id) REFERENCES staff_member (id)
);
CREATE INDEX ix_message_thread_patient_id ON message_thread (patient_id);
CREATE INDEX ix_message_thread_created_by_id ON message_thread (created_by_id);
CREATE INDEX ix_message_thread_last_message_at ON message_thread (last_message_at DESC);

--changeset his:009-thread-participant
CREATE TABLE thread_participant (
    thread_id    uuid        NOT NULL,
    staff_id     uuid        NOT NULL,
    last_read_at timestamptz,
    joined_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_thread_participant PRIMARY KEY (thread_id, staff_id),
    CONSTRAINT fk_thread_participant_thread FOREIGN KEY (thread_id) REFERENCES message_thread (id) ON DELETE CASCADE,
    CONSTRAINT fk_thread_participant_staff FOREIGN KEY (staff_id) REFERENCES staff_member (id)
);
CREATE INDEX ix_thread_participant_staff_id ON thread_participant (staff_id);

--changeset his:009-message
CREATE TABLE message (
    id        uuid        NOT NULL,
    thread_id uuid        NOT NULL,
    sender_id uuid        NOT NULL,
    sent_at   timestamptz NOT NULL DEFAULT now(),
    body      text        NOT NULL,
    priority  varchar(10) NOT NULL DEFAULT 'normal',
    CONSTRAINT pk_message PRIMARY KEY (id),
    CONSTRAINT fk_message_thread FOREIGN KEY (thread_id) REFERENCES message_thread (id) ON DELETE CASCADE,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES staff_member (id),
    CONSTRAINT ck_message_priority CHECK (priority IN ('normal', 'high', 'critical'))
);
CREATE INDEX ix_message_thread_sent_at ON message (thread_id, sent_at DESC);
CREATE INDEX ix_message_sender_id ON message (sender_id);

--changeset his:009-clinical-alert
CREATE TABLE clinical_alert (
    id                uuid         NOT NULL,
    type              varchar(20)  NOT NULL,
    severity          varchar(10)  NOT NULL,
    patient_id        uuid,
    message           text         NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    target_kind       varchar(20),
    target_id         uuid,
    target_patient_id uuid,
    CONSTRAINT pk_clinical_alert PRIMARY KEY (id),
    CONSTRAINT fk_clinical_alert_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_clinical_alert_target_patient FOREIGN KEY (target_patient_id) REFERENCES patient (id),
    CONSTRAINT ck_clinical_alert_type CHECK (type IN
        ('critical_result', 'vital_anomaly', 'order_status', 'task', 'system')),
    CONSTRAINT ck_clinical_alert_severity CHECK (severity IN ('info', 'warning', 'critical')),
    CONSTRAINT ck_clinical_alert_target_kind CHECK (target_kind IS NULL OR target_kind IN
        ('lab_result', 'imaging_result', 'patient_vitals', 'lab_order', 'imaging_order', 'task', 'patient')),
    CONSTRAINT ck_clinical_alert_target_complete CHECK ((target_kind IS NULL) = (target_id IS NULL)),
    CONSTRAINT ck_clinical_alert_target_patient CHECK (target_patient_id IS NULL OR target_kind IS NOT NULL)
);
CREATE INDEX ix_clinical_alert_patient_id ON clinical_alert (patient_id);
CREATE INDEX ix_clinical_alert_target_patient_id ON clinical_alert (target_patient_id);
CREATE INDEX ix_clinical_alert_created_at ON clinical_alert (created_at DESC);
CREATE INDEX ix_clinical_alert_critical ON clinical_alert (created_at DESC) WHERE severity = 'critical';

--changeset his:009-alert-acknowledgement
CREATE TABLE alert_acknowledgement (
    alert_id        uuid        NOT NULL,
    staff_id        uuid        NOT NULL,
    acknowledged_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_alert_acknowledgement PRIMARY KEY (alert_id, staff_id),
    CONSTRAINT fk_alert_acknowledgement_alert FOREIGN KEY (alert_id) REFERENCES clinical_alert (id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_acknowledgement_staff FOREIGN KEY (staff_id) REFERENCES staff_member (id)
);
CREATE INDEX ix_alert_acknowledgement_staff_id ON alert_acknowledgement (staff_id);

--changeset his:009-team-task
CREATE TABLE team_task (
    id             uuid         NOT NULL,
    title          varchar(200) NOT NULL,
    description    text,
    patient_id     uuid,
    assigned_to_id uuid         NOT NULL,
    created_by_id  uuid         NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by_id  uuid,
    due_at         timestamptz,
    priority       varchar(10)  NOT NULL DEFAULT 'normal',
    status         varchar(15)  NOT NULL DEFAULT 'open',
    version        bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_team_task PRIMARY KEY (id),
    CONSTRAINT fk_team_task_patient FOREIGN KEY (patient_id) REFERENCES patient (id),
    CONSTRAINT fk_team_task_assigned_to FOREIGN KEY (assigned_to_id) REFERENCES staff_member (id),
    CONSTRAINT fk_team_task_created_by FOREIGN KEY (created_by_id) REFERENCES staff_member (id),
    CONSTRAINT fk_team_task_updated_by FOREIGN KEY (updated_by_id) REFERENCES staff_member (id),
    CONSTRAINT ck_team_task_priority CHECK (priority IN ('normal', 'high', 'critical')),
    CONSTRAINT ck_team_task_status CHECK (status IN ('open', 'in_progress', 'done', 'cancelled'))
);
CREATE INDEX ix_team_task_assigned_open ON team_task (assigned_to_id, due_at) WHERE status IN ('open', 'in_progress');
CREATE INDEX ix_team_task_patient_id ON team_task (patient_id);
CREATE INDEX ix_team_task_created_by_id ON team_task (created_by_id);
CREATE INDEX ix_team_task_updated_by_id ON team_task (updated_by_id);

--changeset his:009-handoff-note
CREATE TABLE handoff_note (
    id            uuid        NOT NULL,
    ward_id       uuid        NOT NULL,
    shift_date    date        NOT NULL,
    shift         varchar(10) NOT NULL,
    from_id       uuid        NOT NULL,
    to_id         uuid        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    general_notes text,
    CONSTRAINT pk_handoff_note PRIMARY KEY (id),
    CONSTRAINT fk_handoff_note_ward FOREIGN KEY (ward_id) REFERENCES ward (id),
    CONSTRAINT fk_handoff_note_from FOREIGN KEY (from_id) REFERENCES staff_member (id),
    CONSTRAINT fk_handoff_note_to FOREIGN KEY (to_id) REFERENCES staff_member (id),
    CONSTRAINT ck_handoff_note_shift CHECK (shift IN ('day', 'night'))
);
CREATE INDEX ix_handoff_note_ward_shift ON handoff_note (ward_id, shift_date DESC);
CREATE INDEX ix_handoff_note_from_id ON handoff_note (from_id);
CREATE INDEX ix_handoff_note_to_id ON handoff_note (to_id);

--changeset his:009-handoff-patient-note
CREATE TABLE handoff_patient_note (
    handoff_id     uuid NOT NULL,
    patient_id     uuid NOT NULL,
    situation      text NOT NULL,
    background     text NOT NULL,
    assessment     text NOT NULL,
    recommendation text NOT NULL,
    CONSTRAINT pk_handoff_patient_note PRIMARY KEY (handoff_id, patient_id),
    CONSTRAINT fk_handoff_patient_note_handoff FOREIGN KEY (handoff_id) REFERENCES handoff_note (id) ON DELETE CASCADE,
    CONSTRAINT fk_handoff_patient_note_patient FOREIGN KEY (patient_id) REFERENCES patient (id)
);
CREATE INDEX ix_handoff_patient_note_patient_id ON handoff_patient_note (patient_id);
