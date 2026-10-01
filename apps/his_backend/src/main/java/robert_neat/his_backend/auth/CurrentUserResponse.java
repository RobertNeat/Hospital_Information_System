package robert_neat.his_backend.auth;

import java.util.List;
import java.util.UUID;

import robert_neat.his_backend.staff.StaffAccountStatus;
import robert_neat.his_backend.staff.StaffMemberResponse;
import robert_neat.his_backend.staff.StaffRole;

/** `CurrentUser` z kontraktu = `StaffMember` + `permissions` (spłaszczone). */
public record CurrentUserResponse(
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
        boolean online,
        List<String> permissions) {

    public static CurrentUserResponse of(StaffMemberResponse s, List<String> permissions) {
        return new CurrentUserResponse(s.id(), s.title(), s.firstName(), s.lastName(), s.role(), s.specialization(),
                s.wardId(), s.phone(), s.pwz(), s.employeeId(), s.email(), s.accountStatus(), s.online(),
                permissions);
    }
}
