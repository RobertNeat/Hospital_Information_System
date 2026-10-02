--liquibase formatted sql

--changeset his:cleanup-001-drop-icd10-code
-- HIS przechowuje wylacznie SCTID; slownik ICD-10 (`icd10_code`, zasilany z `reference/002-icd10-code.sql`)
-- zastapiono dynamicznym tlumaczeniem SCTID -> ICD-10 przez Snowstorm Lite ($translate, refset 447562003).
DROP TABLE IF EXISTS icd10_code;
