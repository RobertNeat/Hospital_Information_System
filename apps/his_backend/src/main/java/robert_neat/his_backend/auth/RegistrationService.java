package robert_neat.his_backend.auth;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.staff.StaffAccountStatus;
import robert_neat.his_backend.staff.StaffMember;
import robert_neat.his_backend.staff.StaffMemberRepository;
import robert_neat.his_backend.staff.UserAccount;
import robert_neat.his_backend.staff.UserAccountRepository;
import robert_neat.his_backend.staff.WardRepository;

/** Publiczna rejestracja: tworzy pracownika i konto w statusie `pending` (aktywuje administrator). */
@Service
public class RegistrationService {

    private final StaffMemberRepository staff;
    private final UserAccountRepository accounts;
    private final WardRepository wards;
    private final PasswordEncoder passwordEncoder;

    RegistrationService(StaffMemberRepository staff, UserAccountRepository accounts, WardRepository wards,
            PasswordEncoder passwordEncoder) {
        this.staff = staff;
        this.accounts = accounts;
        this.wards = wards;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public StaffRegistrationResponse register(StaffRegistrationRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ValidationFailedException(List.of(
                    new FieldError("password", "Haslo moze miec maksymalnie 72 bajty", "Size")));
        }
        if (!wards.existsById(request.wardId())) {
            throw new ValidationFailedException(List.of(
                    new FieldError("wardId", "Oddzial o podanym identyfikatorze nie istnieje", "notFound")));
        }
        String pwz = blankToNull(request.pwz());
        String email = blankToNull(request.email());
        List<String> duplicates = new ArrayList<>();
        if (staff.existsByEmployeeId(request.employeeId()) || accounts.existsByEmployeeId(request.employeeId())) {
            duplicates.add("employeeId");
        }
        if (pwz != null && staff.existsByPwz(pwz)) {
            duplicates.add("pwz");
        }
        if (email != null && staff.existsByEmailIgnoreCase(email)) {
            duplicates.add("email");
        }
        if (!duplicates.isEmpty()) {
            throw new ConflictException("Rekord o podanych danych juz istnieje: " + String.join(", ", duplicates));
        }

        StaffMember member = staff.saveAndFlush(StaffMember.create(request.title().trim(),
                request.firstName().trim(), request.lastName().trim(), request.role(),
                blankToNull(request.specialization()), request.wardId(), blankToNull(request.phone()), pwz,
                request.employeeId(), email));
        accounts.saveAndFlush(UserAccount.pending(member.getId(), request.employeeId(),
                passwordEncoder.encode(request.password())));
        return new StaffRegistrationResponse(member.getId(), StaffAccountStatus.PENDING);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
