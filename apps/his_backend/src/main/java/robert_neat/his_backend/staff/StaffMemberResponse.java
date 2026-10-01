package robert_neat.his_backend.staff;

import java.util.UUID;

/**
 * `StaffMember` z kontraktu (models/staff.model.ts). Pola opcjonalne (null) sa pomijane (NON_ABSENT).
 * `online` to projekcja obecnosci z {@link PresenceRegistry} (aktywna sesja STOMP; stan w pamieci instancji).
 */
public record StaffMemberResponse(
        UUID id,
        String title,
        String firstName,
        String lastName,
        StaffRole role,
        String specialization,
        UUID wardId,
        String phone,
        String pwz,
        String employeeId,
        String email,
        StaffAccountStatus accountStatus,
        boolean online) {
}
