package com.moviebooking.user.service.auth;

import com.moviebooking.exception.BusinessException;
import com.moviebooking.user.entity.User;
import com.moviebooking.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Template-method base class for login strategies.
 * ABSTRACTION: the shared login steps are written once here.
 * The subclass only states which role it accepts (supportedRole).
 */
public abstract class AbstractAuthStrategy implements AuthStrategy {

    protected static final String INVALID_LOGIN = "Invalid email or password";

    private final UserRepository users;
    private final PasswordEncoder encoder;

    protected AbstractAuthStrategy(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public final User authenticate(String email, String rawPassword) {
        String normalised = email == null ? "" : email.trim();
        User user = users.findByEmailIgnoreCase(normalised)
                .orElseThrow(() -> new BusinessException(INVALID_LOGIN));

        // POLYMORPHISM: user.getRole() is decided by the concrete subclass (Customer / Admin).
        if (user.getRole() != supportedRole()) {
            throw new BusinessException(INVALID_LOGIN);
        }
        if (rawPassword == null || !encoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(INVALID_LOGIN);
        }
        return user;
    }
}
