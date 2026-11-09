package dev.settle.user;

import dev.settle.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void createsUserAndReturnsLocation() throws Exception {
        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "Ann@Example.com", "displayName": "Ann"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("ann@example.com"))
                .andExpect(jsonPath("$.displayName").value("Ann"));
    }

    @Test
    void rejectsInvalidBodyWithFieldErrors() throws Exception {
        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "displayName": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'displayName')]").exists());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        users.save(new User("ann@example.com", "Ann"));

        mvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "ANN@example.com", "displayName": "Other"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A user with this email already exists"));
    }

    @Test
    void returnsExistingUser() throws Exception {
        User ann = users.save(new User("ann@example.com", "Ann"));

        mvc.perform(get("/users/{id}", ann.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ann.getId().toString()))
                .andExpect(jsonPath("$.email").value("ann@example.com"));
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
