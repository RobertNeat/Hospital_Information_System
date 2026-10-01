--liquibase formatted sql

--changeset his:001-ward
CREATE TABLE ward (
    id         uuid         NOT NULL,
    name       varchar(200) NOT NULL,
    short_name varchar(10)  NOT NULL,
    floor      varchar(10)  NOT NULL,
    beds       integer      NOT NULL,
    CONSTRAINT pk_ward PRIMARY KEY (id),
    CONSTRAINT uq_ward_short_name UNIQUE (short_name),
    CONSTRAINT ck_ward_beds CHECK (beds >= 0)
);

--changeset his:001-staff-member
CREATE TABLE staff_member (
    id             uuid         NOT NULL,
    title          varchar(50)  NOT NULL,
    first_name     varchar(100) NOT NULL,
    last_name      varchar(100) NOT NULL,
    role           varchar(30)  NOT NULL,
    specialization varchar(100),
    ward_id        uuid         NOT NULL,
    phone          varchar(30),
    pwz            varchar(7),
    employee_id    varchar(30),
    email          varchar(200),
    CONSTRAINT pk_staff_member PRIMARY KEY (id),
    CONSTRAINT fk_staff_member_ward FOREIGN KEY (ward_id) REFERENCES ward (id),
    CONSTRAINT ck_staff_member_role CHECK (role IN
        ('doctor', 'nurse', 'lab_technician', 'radiologist', 'pharmacist', 'registrar', 'admin')),
    CONSTRAINT ck_staff_member_pwz CHECK (pwz IS NULL OR pwz ~ '^[0-9]{7}$')
);
CREATE INDEX ix_staff_member_ward_id ON staff_member (ward_id);
CREATE INDEX ix_staff_member_role ON staff_member (role);
CREATE INDEX ix_staff_member_last_name ON staff_member (lower(last_name), lower(first_name));
CREATE UNIQUE INDEX uq_staff_member_pwz ON staff_member (pwz) WHERE pwz IS NOT NULL;
CREATE UNIQUE INDEX uq_staff_member_employee_id ON staff_member (employee_id) WHERE employee_id IS NOT NULL;
CREATE UNIQUE INDEX uq_staff_member_email ON staff_member (lower(email)) WHERE email IS NOT NULL;

--changeset his:001-user-account
CREATE TABLE user_account (
    id              uuid         NOT NULL,
    staff_id        uuid         NOT NULL,
    employee_id     varchar(30)  NOT NULL,
    password_hash   varchar(100) NOT NULL,
    account_status  varchar(10)  NOT NULL DEFAULT 'pending',
    failed_attempts integer      NOT NULL DEFAULT 0,
    locked_until    timestamptz,
    last_login_at   timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_user_account PRIMARY KEY (id),
    CONSTRAINT fk_user_account_staff FOREIGN KEY (staff_id) REFERENCES staff_member (id),
    CONSTRAINT uq_user_account_staff_id UNIQUE (staff_id),
    CONSTRAINT uq_user_account_employee_id UNIQUE (employee_id),
    CONSTRAINT ck_user_account_status CHECK (account_status IN ('pending', 'active', 'locked')),
    CONSTRAINT ck_user_account_failed_attempts CHECK (failed_attempts >= 0)
);
