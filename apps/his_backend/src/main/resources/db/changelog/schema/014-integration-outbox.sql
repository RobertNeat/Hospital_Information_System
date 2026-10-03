--liquibase formatted sql

--changeset his:014-integration-outbox
-- Outbox ponawiania nieudanych wysylek do e-receipt/e-laboratory/e-imaging (patrz EReceiptIntegration/
-- ELabIntegration/EImgIntegration + IntegrationOutboxScheduler). Celowo bez payloadu: scheduler odtwarza go
-- z biezacego stanu encji (prescription/lab_order/imaging_order) w chwili ponowienia, a nie z chwili bledu -
-- dzieki temu retry nigdy nie wysyla danych juz nieaktualnych (np. anulowanych miedzyczasie).
CREATE TABLE integration_outbox (
    id              uuid         NOT NULL,
    integration     varchar(10)  NOT NULL,
    operation       varchar(10)  NOT NULL,
    entity_id       uuid         NOT NULL,
    status          varchar(10)  NOT NULL DEFAULT 'pending',
    attempts        integer      NOT NULL DEFAULT 0,
    max_attempts    integer      NOT NULL DEFAULT 8,
    last_error      text,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_integration_outbox PRIMARY KEY (id),
    CONSTRAINT ck_integration_outbox_integration CHECK (integration IN ('ereceipt', 'elab', 'eimg')),
    CONSTRAINT ck_integration_outbox_operation CHECK (operation IN ('submit', 'cancel')),
    CONSTRAINT ck_integration_outbox_status CHECK (status IN ('pending', 'succeeded', 'failed')),
    -- Jeden otwarty wpis na (integracja, operacja, encja): kolejny blad tej samej wysylki aktualizuje
    -- istniejacy wiersz zamiast mnozyc rekordy do ponowienia.
    CONSTRAINT uq_integration_outbox_entry UNIQUE (integration, operation, entity_id)
);
CREATE INDEX ix_integration_outbox_due ON integration_outbox (status, next_attempt_at);
