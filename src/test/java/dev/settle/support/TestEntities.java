package dev.settle.support;

import dev.settle.group.Group;
import dev.settle.user.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

public final class TestEntities {

    private TestEntities() {
    }

    public static User user(String email, String displayName) {
        return withId(new User(email, displayName));
    }

    public static Group group(String name, String currency, User creator) {
        return withId(new Group(name, currency, creator));
    }

    public static <T> T withId(T entity) {
        return withId(entity, UUID.randomUUID());
    }

    public static <T> T withId(T entity, UUID id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
