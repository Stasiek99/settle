package dev.settle.user;

import dev.settle.common.ConflictException;
import dev.settle.common.ResourceNotFoundException;
import dev.settle.support.TestEntities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository users;

    @InjectMocks
    private UserService service;

    @Test
    void createSavesUserWithNormalisedFieldsAndReturnsIt() {
        when(users.existsByEmail("ann@example.com")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0)));

        UserResponse response = service.create(new CreateUserRequest("Ann@Example.com", "  Ann "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ann@example.com");
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Ann");
        assertThat(response.id()).isNotNull();
        assertThat(response.email()).isEqualTo("ann@example.com");
    }

    @Test
    void createRejectsDuplicateEmailAfterNormalising() {
        when(users.existsByEmail("ann@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateUserRequest("  ANN@Example.com", "Ann")))
                .isInstanceOf(ConflictException.class);

        verify(users, never()).save(any());
    }

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
