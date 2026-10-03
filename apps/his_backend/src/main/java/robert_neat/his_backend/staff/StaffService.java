package robert_neat.his_backend.staff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.FieldError;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.api.ValidationFailedException;
import robert_neat.his_backend.common.security.CurrentActor;

@Service
@Transactional(readOnly = true)
public class StaffService {

    private static final Sort ORDER = Sort.by("lastName", "firstName", "id");

    private final StaffMemberRepository staff;
    private final UserAccountRepository accounts;
    private final WardRepository wards;
    private final CurrentActor currentActor;
    private final PresenceRegistry presence;

    StaffService(StaffMemberRepository staff, UserAccountRepository accounts, WardRepository wards,
            CurrentActor currentActor, PresenceRegistry presence) {
        this.staff = staff;
        this.accounts = accounts;
        this.wards = wards;
        this.currentActor = currentActor;
        this.presence = presence;
    }

    public List<StaffMemberResponse> list(StaffRole role, UUID wardId) {
        List<StaffMember> members = staff.search(role, wardId, ORDER);
        Map<UUID, StaffAccountStatus> statuses = statusesOf(members);
        Set<UUID> online = presence.onlineStaffIds();
        return members.stream()
                .map(m -> StaffMapper.toResponse(m, statuses.get(m.getId()), online.contains(m.getId()))).toList();
    }

    /** `id` jest nieprzezroczysty dla klienta: niepoprawny format to po prostu "nie istnieje" (404). */
    public StaffMemberResponse get(String id) {
        UUID uuid = parse(id);
        StaffMember member = uuid == null ? null : staff.findById(uuid).orElse(null);
        if (member == null) {
            throw NotFoundException.of("Pracownik", id);
        }
        return StaffMapper.toResponse(member, statusesOf(List.of(member)).get(member.getId()),
                presence.isOnline(member.getId()));
    }

    /** 404 pracownik; 422 niepoprawny `wardId`; 409 zajety `pwz`/`email`, zmiana wlasnej roli. */
    @Transactional
    public StaffMemberResponse update(String id, StaffUpdateRequest request) {
        UUID uuid = parse(id);
        StaffMember member = uuid == null ? null : staff.findById(uuid).orElse(null);
        if (member == null) {
            throw NotFoundException.of("Pracownik", id);
        }
        if (!wards.existsById(request.wardId())) {
            throw new ValidationFailedException(List.of(
                    new FieldError("wardId", "Oddzial o podanym identyfikatorze nie istnieje", "notFound")));
        }
        if (currentActor.staffId().filter(member.getId()::equals).isPresent() && request.role() != member.getRole()) {
            throw new ConflictException("Nie mozna zmienic wlasnej roli");
        }
        boolean roleChanged = request.role() != member.getRole();
        String pwz = blankToNull(request.pwz());
        String email = blankToNull(request.email());
        List<String> duplicates = new ArrayList<>();
        if (pwz != null && staff.existsByPwzAndIdNot(pwz, member.getId())) {
            duplicates.add("pwz");
        }
        if (email != null && staff.existsByEmailIgnoreCaseAndIdNot(email, member.getId())) {
            duplicates.add("email");
        }
        if (!duplicates.isEmpty()) {
            throw new ConflictException("Rekord o podanych danych juz istnieje: " + String.join(", ", duplicates));
        }
        member.update(request.title().trim(), request.firstName().trim(), request.lastName().trim(), request.role(),
                blankToNull(request.specialization()), request.wardId(), blankToNull(request.phone()), pwz, email);
        staff.saveAndFlush(member);
        // rola jest zapisana w tokenie (claim `role`/`authorities`): zmiana musi uniewaznic juz wydane tokeny
        if (roleChanged) {
            accounts.findByStaffId(member.getId()).ifPresent(UserAccount::bumpTokenVersion);
        }
        return get(id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public StaffMemberResponse activate(String id) {
        UserAccount account = accountOf(id);
        account.activate();
        return get(id);
    }

    @Transactional
    public StaffMemberResponse lock(String id) {
        UserAccount account = accountOf(id);
        if (currentActor.staffId().filter(account.getStaffId()::equals).isPresent()) {
            throw new ConflictException("Nie mozna zablokowac wlasnego konta");
        }
        account.lock();
        return get(id);
    }

    private UserAccount accountOf(String id) {
        UUID uuid = parse(id);
        return (uuid == null ? java.util.Optional.<UserAccount>empty() : accounts.findByStaffId(uuid))
                .orElseThrow(() -> NotFoundException.of("Pracownik", id));
    }

    private Map<UUID, StaffAccountStatus> statusesOf(List<StaffMember> members) {
        Map<UUID, StaffAccountStatus> result = new HashMap<>();
        if (members.isEmpty()) {
            return result;
        }
        accounts.findByStaffIdIn(members.stream().map(StaffMember::getId).toList())
                .forEach(a -> result.put(a.getStaffId(), a.getAccountStatus()));
        return result;
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
