package dev.settle.user;

import dev.settle.common.ResourceNotFoundException;
import dev.settle.support.TestEntities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository users;

    @InjectMocks
    private UserService service;

    @Test
    void getReturnsExistingUser() {
        User ann = TestEntities.user("ann@example.com", "Ann");
        when(users.findById(ann.getId())).thenReturn(Optional.of(ann));

        UserResponse response = service.get(ann.getId());

        assertThat(response.id()).isEqualTo(ann.getId());
        assertThat(response.displayName()).isEqualTo("Ann");
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }
}
