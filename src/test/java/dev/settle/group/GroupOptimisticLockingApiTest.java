package dev.settle.group;

import dev.settle.TestcontainersConfiguration;
import dev.settle.support.TestAuth;
import dev.settle.user.User;
import dev.settle.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GroupOptimisticLockingApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    private User ann;
    private User bob;
    private UUID groupId;

    @BeforeEach
    void createGroup() throws Exception {
        ann = users.save(new User("ann@example.com", "Ann"));
        bob = users.save(new User("bob@example.com", "Bob"));
        TestAuth.authenticateAs(ann.getId());
        String location = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Trip\",\"currency\":\"PLN\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        groupId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private ResultActions rename(String ifMatch, String name) throws Exception {
        var request = patch("/groups/{id}", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}");
        if (ifMatch != null) {
            request.header(HttpHeaders.IF_MATCH, ifMatch);
        }
        return mvc.perform(request);
    }

    @Test
    void getReturnsTheCurrentVersionAsETag() throws Exception {
        mvc.perform(get("/groups/{id}", groupId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void renameWithTheCurrentETagSucceedsAndBumpsTheVersion() throws Exception {
        rename("\"0\"", "Renamed")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""))
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/groups/{id}", groupId))
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"1\""));
    }

    @Test
    void renamingToTheSameNameKeepsTheVersion() throws Exception {
        rename("\"0\"", "Trip")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""));
    }

    @Test
    void aStaleETagIsRejectedAndNothingChanges() throws Exception {
        rename("\"0\"", "First").andExpect(status().isOk());

        rename("\"0\"", "Second")
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.status").value(412));

        mvc.perform(get("/groups/{id}", groupId))
                .andExpect(jsonPath("$.name").value("First"));
    }

    @Test
    void aFutureVersionIsRejectedToo() throws Exception {
        rename("\"7\"", "Nope").andExpect(status().isPreconditionFailed());
    }

    @Test
    void aMissingIfMatchHeaderIsRequired() throws Exception {
        rename(null, "Renamed")
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.status").value(428));
    }

    @ParameterizedTest(name = "If-Match {0} is rejected as malformed")
    @ValueSource(strings = {"0", "\"abc\"", "\"-1\"", "\"\"", "W/\"0\"", "*", "\"0\", \"1\"", "\"99999999999999999999\""})
    void aMalformedIfMatchHeaderIsABadRequest(String value) throws Exception {
        rename(value, "Renamed").andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerMayRename() throws Exception {
        mvc.perform(post("/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + bob.getId() + "\"}"))
                .andExpect(status().isCreated());

        TestAuth.authenticateAs(bob.getId());
        rename("\"0\"", "Hijacked").andExpect(status().isForbidden());

        TestAuth.authenticateAs(ann.getId());
        mvc.perform(get("/groups/{id}", groupId))
                .andExpect(jsonPath("$.name").value("Trip"));
    }

    @Test
    void aNonMemberSeesTheGroupAsNotFound() throws Exception {
        TestAuth.authenticateAs(bob.getId());
        rename("\"0\"", "Hijacked").andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "name \"{0}\" is rejected")
    @ValueSource(strings = {"", "   "})
    void rejectsABlankName(String name) throws Exception {
        rename("\"0\"", name).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsATooLongName() throws Exception {
        rename("\"0\"", "x".repeat(101)).andExpect(status().isBadRequest());
    }
}
