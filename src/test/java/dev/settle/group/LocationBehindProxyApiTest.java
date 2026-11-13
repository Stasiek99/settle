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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "settle.web.public-path=/api")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LocationBehindProxyApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void signIn() {
        User ann = users.save(new User("ann@example.com", "Ann"));
        TestAuth.authenticateAs(ann.getId());
    }

    @Test
    void theLocationOfACreatedResourceIncludesThePublicPath() throws Exception {
        String location = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Trip\",\"currency\":\"EUR\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).matches("/api/groups/[0-9a-f-]{36}");
    }
}
