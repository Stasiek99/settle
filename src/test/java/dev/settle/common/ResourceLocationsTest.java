package dev.settle.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceLocationsTest {

    private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private URI locationFor(String publicPath, String requestUri, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", requestUri);
        request.setServerName("example.com");
        request.setServerPort(80);
        request.setQueryString(query);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return new ResourceLocations(new WebProperties(publicPath)).ofChild(id);
    }

    @Test
    void isAPathThatDoesNotDependOnHostPortOrScheme() {
        assertThat(locationFor("", "/groups", null).isAbsolute()).isFalse();
    }

    @Test
    void appendsTheNewIdToTheRequestPath() {
        assertThat(locationFor("", "/groups", null))
                .hasToString("/groups/" + id);
    }

    @Test
    void putsThePublicPathInFrontWhenAProxyStripsIt() {
        assertThat(locationFor("/api", "/groups/g1/expenses", null))
                .hasToString("/api/groups/g1/expenses/" + id);
    }

    @Test
    void leavesTheQueryStringOut() {
        assertThat(locationFor("/api", "/groups", "debug=1"))
                .hasToString("/api/groups/" + id);
    }
}
