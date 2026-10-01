package robert_neat.his_backend.auth;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import robert_neat.his_backend.security.RolePermissions;
import robert_neat.his_backend.staff.StaffAccountStatus;
import robert_neat.his_backend.staff.StaffMember;
import robert_neat.his_backend.staff.UserAccount;

/** Zarzadzane encje konta i pracownika (w obrebie transakcji) widziane jako {@link UserDetails}. */
public record HisUserDetails(UserAccount account, StaffMember staff) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = RolePermissions.authoritiesOf(staff.getRole()).stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        return authorities;
    }

    @Override
    public String getPassword() {
        return account.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return account.getEmployeeId();
    }

    @Override
    public boolean isAccountNonLocked() {
        return account.getAccountStatus() != StaffAccountStatus.LOCKED && !account.isTemporarilyLocked(Instant.now());
    }

    @Override
    public boolean isEnabled() {
        return account.getAccountStatus() == StaffAccountStatus.ACTIVE;
    }
}
