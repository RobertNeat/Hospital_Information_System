package robert_neat.his_backend.auth;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import robert_neat.his_backend.staff.StaffMemberRepository;
import robert_neat.his_backend.staff.UserAccount;
import robert_neat.his_backend.staff.UserAccountRepository;

/** Ladowanie konta po `user_account.employee_id` (z blokada zapisu wiersza konta - dla licznika prob). */
@Service
public class HisUserDetailsService implements UserDetailsService {

    private final UserAccountRepository accounts;
    private final StaffMemberRepository staff;

    HisUserDetailsService(UserAccountRepository accounts, StaffMemberRepository staff) {
        this.accounts = accounts;
        this.staff = staff;
    }

    /** Wywolywac w transakcji: zwrocone encje sa zarzadzane, a wiersz konta zablokowany do jej konca. */
    @Override
    @Transactional
    public HisUserDetails loadUserByUsername(String employeeId) {
        UserAccount account = accounts.findByEmployeeIdForUpdate(employeeId)
                .orElseThrow(() -> new UsernameNotFoundException("Brak konta"));
        return new HisUserDetails(account, staff.findById(account.getStaffId())
                .orElseThrow(() -> new UsernameNotFoundException("Brak pracownika konta")));
    }
}
