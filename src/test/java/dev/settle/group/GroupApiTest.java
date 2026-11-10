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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GroupApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    private User ann;
    private User bob;

    @BeforeEach
    void createUsers() {
        ann = users.save(new User("ann@example.com", "Ann"));
        bob = users.save(new User("bob@example.com", "Bob"));
        TestAuth.authenticateAs(ann.getId());
    }

    @Test
    void createsGroupAndMakesTheCallerTheOwner() throws Exception {
        String location = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupJson("Trip to Rome", "eur")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Trip to Rome"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.createdBy").value(ann.getId().toString()))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(ann.getId().toString()))
                .andExpect(jsonPath("$[0].displayName").value("Ann"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void rejectsInvalidGroup() throws Exception {
        mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupJson("", "ZLOTY")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'currency')]").exists());
    }

    @Test
    void addsMemberAndRejectsDuplicate() throws Exception {
        UUID groupId = createGroup("Flat");
        String addBob = "{\"userId\": \"%s\"}".formatted(bob.getId());

        mvc.perform(post("/groups/{id}/members", groupId).contentType(MediaType.APPLICATION_JSON).content(addBob))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(bob.getId().toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        mvc.perform(post("/groups/{id}/members", groupId).contentType(MediaType.APPLICATION_JSON).content(addBob))
                .andExpect(status().isConflict());

        mvc.perform(get("/groups/{id}/members", groupId))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void returns404ForUnknownGroupOrUnknownUserToAdd() throws Exception {
        UUID groupId = createGroup("Flat");

        mvc.perform(get("/groups/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/groups/{id}/members", UUID.randomUUID())).andExpect(status().isNotFound());

        mvc.perform(post("/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"%s\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAddMemberWithoutUserId() throws Exception {
        UUID groupId = createGroup("Flat");

        mvc.perform(post("/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'userId')]").exists());
    }

    @Test
    void listsOnlyTheGroupsTheCallerBelongsToNewestFirst() throws Exception {
        createGroup("First");
        createGroup("Second");
        TestAuth.authenticateAs(bob.getId());
        createGroup("Bobs");

        TestAuth.authenticateAs(ann.getId());
        mvc.perform(get("/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Second"))
                .andExpect(jsonPath("$[1].name").value("First"));

        TestAuth.authenticateAs(bob.getId());
        mvc.perform(get("/groups"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Bobs"));
    }

    @Test
    void aUserWithoutGroupsGetsAnEmptyList() throws Exception {
        mvc.perform(get("/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private UUID createGroup(String name) throws Exception {
        String json = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createGroupJson(name, "PLN")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
    }

    private static String createGroupJson(String name, String currency) {
        return """
                {"name": "%s", "currency": "%s"}
                """.formatted(name, currency);
    }
}
