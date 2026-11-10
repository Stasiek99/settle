package dev.settle.auth;

import dev.settle.common.AuthenticationFailedException;
import dev.settle.common.ConflictException;
import dev.settle.common.InvalidRequestException;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import dev.settle.user.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String unknownUserHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.unknownUserHash = passwordEncoder.encode("password-for-users-that-do-not-exist");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (exceedsBcryptLimit(request.password())) {
            throw new InvalidRequestException("Password must be at most " + MAX_PASSWORD_BYTES + " bytes long");
        }
        if (users.existsByEmail(User.normalizeEmail(request.email()))) {
            throw new ConflictException("A user with this email already exists");
        }
        User saved = users.save(new User(
                request.email(), request.displayName(), passwordEncoder.encode(request.password())));
        log.atInfo().addKeyValue("user.id", saved.getId()).log("User registered");
        return UserResponse.from(saved);
    }

    public TokenResponse login(LoginRequest request) {
        if (exceedsBcryptLimit(request.password())) {
            log.warn("Login rejected: password over the length limit");
            throw new AuthenticationFailedException();
        }
        Optional<User> user = users.findByEmail(User.normalizeEmail(request.email()));
        String storedHash = user.map(User::getPasswordHash).orElse(null);

        boolean passwordMatches = passwordEncoder.matches(
                request.password(), storedHash != null ? storedHash : unknownUserHash);
        if (user.isEmpty() || storedHash == null || !passwordMatches) {
            log.warn("Login failed: invalid credentials");
            throw new AuthenticationFailedException();
        }
        log.atInfo().addKeyValue("user.id", user.get().getId()).log("User logged in");
        return jwtService.issueFor(user.get());
    }

    private static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }
}
