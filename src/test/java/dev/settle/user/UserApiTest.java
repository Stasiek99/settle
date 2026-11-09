package dev.settle.user;

import dev.settle.TestcontainersConfiguration;
import dev.settle.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class UserApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Test
    void returnsTheCallersOwnProfile() throws Exception {
        User ann = users.save(new User("ann@example.com", "Ann"));
        users.save(new User("bob@example.com", "Bob"));
        TestAuth.authenticateAs(ann.getId());

        mvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ann.getId().toString()))
                .andExpect(jsonPath("$.email").value("ann@example.com"));
    }

    @Test
    void neverExposesCredentials() throws Exception {
        User ann = users.save(new User("ann@example.com", "Ann", "{bcrypt}hash"));
        TestAuth.authenticateAs(ann.getId());

        mvc.perform(get("/users/me"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void thereIsNoWayToReadSomeoneElsesProfileById() throws Exception {
        User ann = users.save(new User("ann@example.com", "Ann"));
        User bob = users.save(new User("bob@example.com", "Bob"));
        TestAuth.authenticateAs(ann.getId());

        mvc.perform(get("/users/{id}", bob.getId())).andExpect(status().isNotFound());
    }

    @Test
    void aTokenOfAUserThatNoLongerExistsFindsNothing() throws Exception {
        TestAuth.authenticateAs(UUID.randomUUID());

        mvc.perform(get("/users/me")).andExpect(status().isNotFound());
    }
}
