package com.fintrack.api.security;

import com.fintrack.api.repository.UserRepository;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository users;
    public CustomUserDetailsService(UserRepository users) { this.users = users; }
    @Override public AuthenticatedUser loadUserByUsername(String identifier) {
        return users.findByIdentifier(identifier.trim()).map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
    public AuthenticatedUser loadById(UUID id) {
        return users.findById(id).map(AuthenticatedUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
