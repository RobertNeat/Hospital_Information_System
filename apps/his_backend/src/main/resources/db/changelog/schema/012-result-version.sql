--liquibase formatted sql

--changeset his:012-lab-result-version
ALTER TABLE lab_result ADD COLUMN version bigint NOT NULL DEFAULT 0;

--changeset his:012-imaging-result-version
ALTER TABLE imaging_result ADD COLUMN version bigint NOT NULL DEFAULT 0;
