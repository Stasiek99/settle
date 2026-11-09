package dev.settle.user;

import dev.settle.common.ConflictException;
import dev.settle.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (users.existsByEmail(User.normalizeEmail(request.email()))) {
            throw new ConflictException("A user with this email already exists");
        }
        User saved = users.save(new User(request.email(), request.displayName()));
        return UserResponse.from(saved);
    }

    public UserResponse get(UUID id) {
        return users.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
