package dev.settle.auth;

import dev.settle.common.AuthenticationFailedException;
import dev.settle.user.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;
    private final SessionCookies cookies;

    public AuthController(AuthService service, SessionCookies cookies) {
        this.service = service;
        this.cookies = cookies;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        AuthService.Session session = service.login(request);
        cookies.write(response, session.access(), session.refresh());
        return ResponseEntity.ok(session.user());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            String refreshToken = cookies.refreshToken(request)
                    .orElseThrow(() -> new AuthenticationFailedException("The session has ended. Please sign in again."));
            AuthService.Session session = service.refresh(refreshToken);
            cookies.write(response, session.access(), session.refresh());
            return ResponseEntity.noContent().build();
        } catch (AuthenticationFailedException e) {
            cookies.clear(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        cookies.refreshToken(request).ifPresent(service::logout);
        cookies.clear(response);
        return ResponseEntity.noContent().build();
    }
}
