package robert_neat.his_backend.staff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ConflictException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.common.security.CurrentActor;

@Service
@Transactional(readOnly = true)
public class StaffService {

    private static final Sort ORDER = Sort.by("lastName", "firstName", "id");

    private final StaffMemberRepository staff;
    private final UserAccountRepository accounts;
    private final CurrentActor currentActor;
    private final PresenceRegistry presence;

    StaffService(StaffMemberRepository staff, UserAccountRepository accounts, CurrentActor currentActor,
            PresenceRegistry presence) {
        this.staff = staff;
        this.accounts = accounts;
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
