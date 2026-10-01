package robert_neat.his_backend.common.security;

import java.util.Optional;
import java.util.UUID;

/** Aktor biezacego zadania (zalogowany pracownik). Zrodlo dla audytu i pol "aktor z sesji". */
public interface CurrentActor {

    /** `staff_member.id` zalogowanego pracownika albo pusty (brak sesji / brak powiazania z pracownikiem). */
    Optional<UUID> staffId();
}
