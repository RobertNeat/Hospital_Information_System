--liquibase formatted sql

--changeset his:013-user-account-token-version
-- Stempel wersji tokenu: bazowy mechanizm odwolywania JWT (wydane tokeny nosza wersje jako claim;
-- kazde zadanie porownuje ja z wartoscia w bazie - niezgodnosc = 401). Bump przy blokadzie konta i
-- zmianie roli (patrz StaffService). Domyslnie 0 dla istniejacych i nowych kont.
ALTER TABLE user_account ADD COLUMN token_version integer NOT NULL DEFAULT 0;
