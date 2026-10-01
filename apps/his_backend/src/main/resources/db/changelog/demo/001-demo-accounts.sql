--liquibase formatted sql
-- Konta proste (login = identyfikator = haslo), RECZNIE pisane; ladowane takze na produkcji (kontekst `reference`).
-- Niezalezne od danych `mock`. Hashe BCrypt (cost 10, bez prefiksu {bcrypt}) policzone raz; ID stale (UUID z nazw).
-- UWAGA: slabe hasla - patrz .github/pipeline_docs/production_deployment.md (zmienic/zablokowac na hostach w LAN).

--changeset his-demo:001-demo-ward context:reference
INSERT INTO ward (id, name, short_name, floor, beds) VALUES
    ('d8e88b10-bf2b-3513-9d82-1f4d60114c5f', 'Administracja / konta demo', 'DEMO', '0', 0);

--changeset his-demo:001-demo-staff-member context:reference
INSERT INTO staff_member (id, title, first_name, last_name, role, specialization, ward_id, employee_id) VALUES
    ('411ab119-b4dc-3ec9-8d30-19482941dea5', 'mgr', 'Demo', 'Admin', 'admin', NULL, 'd8e88b10-bf2b-3513-9d82-1f4d60114c5f', 'admin'),
    ('d1576457-89bd-3990-8ead-f410d3dedc76', 'lek.', 'Demo', 'Użytkownik', 'doctor', 'Medycyna rodzinna', 'd8e88b10-bf2b-3513-9d82-1f4d60114c5f', 'user'),
    ('3bc5ba72-1a62-3681-ba58-fa6c83501852', 'lek.', 'Demo', 'Lekarz', 'doctor', 'Choroby wewnętrzne', 'd8e88b10-bf2b-3513-9d82-1f4d60114c5f', 'doctor'),
    ('28222254-25c0-34db-938f-9228b7a20c52', 'piel.', 'Demo', 'Pielęgniarka', 'nurse', NULL, 'd8e88b10-bf2b-3513-9d82-1f4d60114c5f', 'nurse'),
    ('353211f4-0d37-3369-8533-001c6946e7d7', 'mgr', 'Demo', 'Laborant', 'lab_technician', 'Diagnostyka laboratoryjna', 'd8e88b10-bf2b-3513-9d82-1f4d60114c5f', 'lab-tech'),
    ('1f93f564-8243-3ee5-8e75-a99cc5dead7c', 'lek.', 'Demo', 'Radiolog', 'radiologist', 'Radiologia', 'd8e88b10-bf2b-3513-9d82-1f4d60114c5f', 'radiologist'),
    ('0561ba93-d01d-34da-b612-54645d7af445', 'mgr farm.', 'Demo', 'Farmaceuta', 'pharmacist', NULL, 'd8e88b10-bf2b-3513-9d82-1f4d60114c5f', 'pharmacist'),
    ('b1765703-e1e1-349e-acc6-d7d5bf524084', 'mgr', 'Demo', 'Rejestrator', 'registrar', NULL, 'd8e88b10-bf2b-3513-9d82-1f4d60114c5f', 'registrar');

--changeset his-demo:001-demo-user-account context:reference
INSERT INTO user_account (id, staff_id, employee_id, password_hash, account_status) VALUES
    ('dd66e2c7-5540-331f-b553-ee0410c479cb', '411ab119-b4dc-3ec9-8d30-19482941dea5', 'admin', '$2a$10$8ODOqRJDToGFsXgazfcyAOz.Psx4xod964lGP.S0gwwZV82SAhbry', 'active'),
    ('67bdfd46-54d8-3035-bb58-fb6229c281b6', 'd1576457-89bd-3990-8ead-f410d3dedc76', 'user', '$2a$10$Ts/fszTFNSWn/..I5qak5OrsZGIce7Xd9HesKf5xNnejTfxqcDCmG', 'active'),
    ('0602bd0d-73e9-3955-800a-05073ff3cf87', '3bc5ba72-1a62-3681-ba58-fa6c83501852', 'doctor', '$2a$10$XvsYjeDA3X972KMV2UoUMeEwftMki4fe8oKG4QPfWd7W/7l2z.Uw2', 'active'),
    ('90e2fe50-e58f-3b07-ad28-991e6f1194ae', '28222254-25c0-34db-938f-9228b7a20c52', 'nurse', '$2a$10$YFrHM.0R8ZP6f1iIbydDNuOuQ48hJ2aDbl4FztkAbSrhQVAVLoae2', 'active'),
    ('4545cddc-1055-3ea5-b6fe-95bbd76b6784', '353211f4-0d37-3369-8533-001c6946e7d7', 'lab-tech', '$2a$10$uGGxeLC33NZSj/lHvfjh/u./HvuPJdqFHoMBQfm0Sqspsr36XRMA6', 'active'),
    ('b17fdd05-35ad-3f26-956e-92e78cf17554', '1f93f564-8243-3ee5-8e75-a99cc5dead7c', 'radiologist', '$2a$10$zZnReQRPKcBaqots6jDAr.J9cZiRyx0c6cQ8iR675rbHRBuprIhk6', 'active'),
    ('87d8efca-cd15-3d20-9105-547f6c785a97', '0561ba93-d01d-34da-b612-54645d7af445', 'pharmacist', '$2a$10$kyyvvWOln4tSk0UJ6D5.qO0D2KpVZQKLVras5WsoJKJ4CRXFg1vny', 'active'),
    ('02714070-7bbf-3d72-825a-2edc7bb2f13c', 'b1765703-e1e1-349e-acc6-d7d5bf524084', 'registrar', '$2a$10$BNiTr1i.RXKGh8/XEVU6TOna1saMVJ98NXYj0FljUUnIm8AYxHuVq', 'active');
