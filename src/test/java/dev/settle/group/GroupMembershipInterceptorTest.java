package dev.settle.group;

import dev.settle.common.ResourceNotFoundException;
import dev.settle.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GroupMembershipInterceptorTest {

    @Mock
    private GroupAccess access;

    private final UUID user = UUID.randomUUID();
    private final UUID group = UUID.randomUUID();

    @AfterEach
    void clearSecurityContext() {
        TestSecurityContextHolder.clearContext();
        SecurityContextHolder.clearContext();
    }

    private boolean preHandle(Map<String, String> pathVariables) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (pathVariables != null) {
            request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVariables);
        }
        return new GroupMembershipInterceptor(access).preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void checksMembershipOfTheCallerInTheGroupFromThePath() {
        TestAuth.authenticateAs(user);

        assertThat(preHandle(Map.of("groupId", group.toString()))).isTrue();

        verify(access).requireMember(group, user);
    }

    @Test
    void letsTheRequestThroughWhenTheCallerIsNotAMemberOnlyByThrowingNotFound() {
        TestAuth.authenticateAs(user);
        doThrow(new ResourceNotFoundException("Group", group)).when(access).requireMember(group, user);

        assertThatThrownBy(() -> preHandle(Map.of("groupId", group.toString())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ignoresRoutesWithoutAGroupId() {
        assertThat(preHandle(null)).isTrue();
        assertThat(preHandle(Map.of("other", "x"))).isTrue();

        verifyNoInteractions(access);
    }

    @Test
    void leavesAMalformedGroupIdToTheControllerWhichAnswersBadRequest() {
        TestAuth.authenticateAs(user);

        assertThat(preHandle(Map.of("groupId", "not-a-uuid"))).isTrue();

        verifyNoInteractions(access);
    }
}
