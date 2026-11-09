package dev.settle.group;

import dev.settle.common.ForbiddenException;
import dev.settle.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class GroupAccessTest {

    private final UUID group = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @Mock
    private MembershipRepository memberships;

    @InjectMocks
    private GroupAccess access;

    @Test
    void aMemberPassesTheMemberCheck() {
        when(memberships.existsByGroupIdAndUserId(group, user)).thenReturn(true);

        assertThatCode(() -> access.requireMember(group, user)).doesNotThrowAnyException();
    }

    @Test
    void aNonMemberGetsNotFoundNotForbidden() {
        when(memberships.existsByGroupIdAndUserId(group, user)).thenReturn(false);

        assertThatThrownBy(() -> access.requireMember(group, user)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theOwnerPassesTheOwnerCheck() {
        when(memberships.existsByGroupIdAndUserId(group, user)).thenReturn(true);
        when(memberships.existsByGroupIdAndUserIdAndRole(group, user, MembershipRole.OWNER)).thenReturn(true);

        assertThatCode(() -> access.requireOwner(group, user)).doesNotThrowAnyException();
    }

    @Test
    void aPlainMemberIsForbiddenFromOwnerActions() {
        when(memberships.existsByGroupIdAndUserId(group, user)).thenReturn(true);
        when(memberships.existsByGroupIdAndUserIdAndRole(group, user, MembershipRole.OWNER)).thenReturn(false);

        assertThatThrownBy(() -> access.requireOwner(group, user)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aNonMemberAskingForOwnerActionsLearnsNothingAboutTheGroup() {
        when(memberships.existsByGroupIdAndUserId(group, user)).thenReturn(false);

        assertThatThrownBy(() -> access.requireOwner(group, user)).isInstanceOf(ResourceNotFoundException.class);

        verify(memberships, never()).existsByGroupIdAndUserIdAndRole(any(), any(), any());
    }
}
