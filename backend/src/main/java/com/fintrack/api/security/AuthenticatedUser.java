package com.fintrack.api.security;

import com.fintrack.api.repository.UserRepository.UserRecord;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Future OAuth providers can resolve to the same application principal and UUID. */
public final class AuthenticatedUser implements UserDetails {
    private final UserRecord user;
    public AuthenticatedUser(UserRecord user) { this.user = user; }
    public UUID id() { return user.id(); }
    public UserRecord user() { return user; }
    @Override public String getUsername() { return user.username(); }
    @Override public String getPassword() { return user.passwordHash(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
    }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return !"LOCKED".equals(user.status()); }
    @Override public boolean isEnabled() { return !"DISABLED".equals(user.status()); }
}
