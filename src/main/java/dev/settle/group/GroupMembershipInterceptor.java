package dev.settle.group;

import dev.settle.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

class GroupMembershipInterceptor implements HandlerInterceptor {

    static final String GROUP_ID_VARIABLE = "groupId";

    private final GroupAccess access;

    GroupMembershipInterceptor(GroupAccess access) {
        this.access = access;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Map<String, String> variables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String rawGroupId = variables == null ? null : variables.get(GROUP_ID_VARIABLE);
        if (rawGroupId == null) {
            return true;
        }
        UUID groupId;
        try {
            groupId = UUID.fromString(rawGroupId);
        } catch (IllegalArgumentException e) {
            return true;
        }
        access.requireMember(groupId, CurrentUser.fromSecurityContext().id());
        return true;
    }
}
