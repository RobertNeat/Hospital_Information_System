package robert_neat.his_backend.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.common.api.ForbiddenException;
import robert_neat.his_backend.common.api.NotFoundException;
import robert_neat.his_backend.security.JwtTokenService;
import robert_neat.his_backend.security.JwtTokenService.IssuedToken;
import robert_neat.his_backend.security.LockoutProperties;
import robert_neat.his_backend.security.RolePermissions;
import robert_neat.his_backend.staff.PresenceRegistry;
import robert_neat.his_backend.staff.StaffAccountStatus;
import robert_neat.his_backend.staff.StaffMapper;
import robert_neat.his_backend.staff.StaffMember;
import robert_neat.his_backend.staff.StaffMemberRepository;
import robert_neat.his_backend.staff.UserAccount;
import robert_neat.his_backend.staff.UserAccountRepository;

/**
 * Logowanie (login + haslo -> JWT) z polityka blokady oraz odczyt biezacego uzytkownika.
 * <p>
 * Kolejnosc sprawdzen (bez enumeracji kont): nieznany login i zle haslo daja identyczne 401;
 * aktywna blokada czasowa -> 403 od razu; status `pending`/`locked` -> 403 dopiero po poprawnym hasle.
 * Po wygasnieciu blokady czasowej licznik prob zaczyna sie od zera (zerowany w momencie nalozenia blokady).
 * Nieudana proba musi zostac zapisana mimo wyjatku, stad {@code noRollbackFor}.
 */
@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final HisUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final LockoutProperties lockout;
    private final StaffMemberRepository staff;
    private final UserAccountRepository accounts;
    private final PresenceRegistry presence;
    /** Hash do wyrownania czasu odpowiedzi dla nieznanego loginu. */
    private final String dummyHash;

    AuthService(HisUserDetailsService userDetailsService, PasswordEncoder passwordEncoder, JwtTokenService tokens,
            LockoutProperties lockout, StaffMemberRepository staff, UserAccountRepository accounts,
            PresenceRegistry presence) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.lockout = lockout;
        this.staff = staff;
        this.accounts = accounts;
        this.presence = presence;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = {BadCredentialsException.class, ForbiddenException.class})
    public LoginResponse login(LoginRequest request) {
        HisUserDetails details = load(request.employeeId());
        if (details == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw invalidCredentials();
        }
        UserAccount account = details.account();
        Instant now = Instant.now();

        if (account.isTemporarilyLocked(now)) {
            throw new ForbiddenException("Konto jest tymczasowo zablokowane po nieudanych probach logowania");
        }
        if (!passwordMatches(request.password(), account.getPasswordHash())) {
            if (account.getAccountStatus() == StaffAccountStatus.ACTIVE) {
                account.registerFailedAttempt(now, lockout.maxAttempts(), lockout.duration());
            }
            throw invalidCredentials();
        }
        switch (account.getAccountStatus()) {
            case PENDING -> throw new ForbiddenException("Konto oczekuje na aktywacje przez administratora");
            case LOCKED -> throw new ForbiddenException("Konto jest zablokowane");
            case ACTIVE -> {
                // konto aktywne: logowanie dozwolone
            }
        }
        account.registerSuccessfulLogin(now);

        StaffMember member = details.staff();
        IssuedToken token = tokens.issue(account.getId(), member.getId(), account.getEmployeeId(), member.getRole(),
                member.getWardId());
        return new LoginResponse(token.value(), currentUser(member, account.getAccountStatus()), token.expiresAt());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(UUID staffId) {
        StaffMember member = staff.findById(staffId).orElseThrow(() -> NotFoundException.of("Pracownik", staffId));
        StaffAccountStatus status = accounts.findByStaffId(staffId).map(UserAccount::getAccountStatus).orElse(null);
        return currentUser(member, status);
    }

    private CurrentUserResponse currentUser(StaffMember member, StaffAccountStatus status) {
        return CurrentUserResponse.of(StaffMapper.toResponse(member, status, presence.isOnline(member.getId())),
                RolePermissions.permissionsOf(member.getRole()));
    }

    private HisUserDetails load(String employeeId) {
        try {
            return userDetailsService.loadUserByUsername(employeeId);
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    private boolean passwordMatches(String raw, String hash) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return false;
        }
        return passwordEncoder.matches(raw, hash);
    }

    private static BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Niepoprawny identyfikator lub haslo");
    }
}
