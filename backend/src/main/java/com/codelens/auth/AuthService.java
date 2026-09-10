package com.codelens.auth;

import com.codelens.common.ConflictException;
import com.codelens.common.UnauthorizedException;
import com.codelens.domain.AppUser;
import com.codelens.repository.ProjectRepository;
import com.codelens.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    public record Session(String token, AppUser user) {
    }

    private final UserRepository users;
    private final ProjectRepository projects;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    // checked for unknown emails so response time does not reveal accounts
    private final String dummyHash;

    public AuthService(UserRepository users, ProjectRepository projects, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.projects = projects;
        this.encoder = encoder;
        this.tokens = tokens;
        this.dummyHash = encoder.encode("codelens-no-such-user");
    }

    @Transactional
    public Session register(String name, String email, String password) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) throw new ConflictException("an account with this email already exists");
        boolean first = users.count() == 0;

        var user = new AppUser();
        user.setName(name.trim());
        user.setEmail(normalized);
        user.setPasswordHash(encoder.encode(password));
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("an account with this email already exists");
        }
        // first account adopts projects created before sign-in existed
        if (first) projects.adoptUnowned(user.getId());
        return new Session(tokens.issue(user), user);
    }

    public Session login(String email, String password) {
        Optional<AppUser> user = users.findByEmail(normalize(email));
        boolean matches = encoder.matches(password, user.map(AppUser::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) throw new UnauthorizedException("invalid email or password");
        return new Session(tokens.issue(user.get()), user.get());
    }

    public AppUser get(long id) {
        return users.findById(id).orElseThrow(() -> new UnauthorizedException("account no longer exists"));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
