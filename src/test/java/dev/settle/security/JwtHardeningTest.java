package dev.settle.security;

import dev.settle.TestcontainersConfiguration;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class JwtHardeningTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private JwtEncoder encoder;

    private User ann;

    @BeforeEach
    void createUser() {
        ann = users.save(new User("ann@example.com", "Ann"));
    }

    private String signed(Consumer<JwtClaimsSet.Builder> customize) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("settle")
                .subject(ann.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)));
        customize.accept(claims);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private org.springframework.test.web.servlet.ResultActions callWith(String token) throws Exception {
        return mvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void aCorrectlyBuiltTokenIsAccepted() throws Exception {
        callWith(signed(claims -> { })).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ann.getId().toString()));
    }

    @Test
    void aTokenWithoutAnExpiryIsRefusedEvenWhenTheSignatureIsValid() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("settle").subject(ann.getId().toString()).issuedAt(now).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        callWith(token).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenThatIsNotValidYetIsRefused() throws Exception {
        callWith(signed(claims -> claims.notBefore(Instant.now().plus(Duration.ofHours(1)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWhoseSubjectIsNotAUserIdIsRefused() throws Exception {
        callWith(signed(claims -> claims.subject("admin"))).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutASubjectIsRefused() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("settle").issuedAt(now).expiresAt(now.plus(Duration.ofHours(1))).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        callWith(token).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenOfSomeoneElsesUserIdOnlyEverActsAsThatUser() throws Exception {
        User bob = users.save(new User("bob@example.com", "Bob"));

        callWith(signed(claims -> claims.subject(bob.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob@example.com"));
    }

    @Test
    void twoAuthorizationHeadersAreRefused() throws Exception {
        String token = signed(claims -> { });

        mvc.perform(get("/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anAbsurdlyLongTokenIsRefusedWithoutAnError() throws Exception {
        callWith("a".repeat(200_000)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokensWithTheWrongShapeAreRefused() throws Exception {
        for (String token : new String[]{"", " ", "a.b", "a.b.c", "....", "%00", "null", "undefined", "Bearer"}) {
            callWith(token).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void aValidTokenForAUserThatDoesNotExistFindsNoProfile() throws Exception {
        callWith(signed(claims -> claims.subject(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
    }
}
