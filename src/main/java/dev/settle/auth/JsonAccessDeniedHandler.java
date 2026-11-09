package dev.settle.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        bearer.handle(request, response, exception);
        ProblemJson.write(response, HttpStatus.FORBIDDEN, "You do not have permission to access this resource");
    }
}
