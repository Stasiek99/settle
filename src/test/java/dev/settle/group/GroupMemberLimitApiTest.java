package dev.settle.group;

import dev.settle.TestcontainersConfiguration;
import dev.settle.support.TestAuth;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "settle.groups.max-members=2")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GroupMemberLimitApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    private User bob;
    private User cleo;
    private UUID groupId;

    @BeforeEach
    void setUp() throws Exception {
        User ann = users.save(new User("ann@example.com", "Ann"));
        bob = users.save(new User("bob@example.com", "Bob"));
        cleo = users.save(new User("cleo@example.com", "Cleo"));
        TestAuth.authenticateAs(ann.getId());
        String location = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flat\",\"currency\":\"EUR\"}"))
                .andReturn().getResponse().getHeader("Location");
        groupId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private void add(User user, int expectedStatus) throws Exception {
        mvc.perform(post("/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"%s\"}".formatted(user.getId())))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void refusesAMemberBeyondTheLimit() throws Exception {
        add(bob, 201);
        add(cleo, 409);

        mvc.perform(get("/groups/{id}/members", groupId))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
