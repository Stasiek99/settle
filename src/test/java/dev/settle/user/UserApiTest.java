package dev.settle.user;

import dev.settle.TestcontainersConfiguration;
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
    void returnsExistingUser() throws Exception {
        User ann = users.save(new User("ann@example.com", "Ann"));

        mvc.perform(get("/users/{id}", ann.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ann.getId().toString()))
                .andExpect(jsonPath("$.email").value("ann@example.com"));
    }

    @Test
    void neverExposesCredentials() throws Exception {
        User ann = users.save(new User("ann@example.com", "Ann", "{bcrypt}hash"));

        mvc.perform(get("/users/{id}", ann.getId()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void returns404ForUnknownUser() throws Exception {
        mvc.perform(get("/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returns400ForMalformedId() throws Exception {
        mvc.perform(get("/users/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }
}
