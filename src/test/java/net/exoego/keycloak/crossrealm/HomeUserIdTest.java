package net.exoego.keycloak.crossrealm;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeUserIdTest {

    @Test
    void parsesTheRealmIdAndTheUserId() {
        assertEquals(Optional.of(new HomeUserId("realm-1", "user-1")), HomeUserId.parse("realm-1:user-1"));
    }

    @Test
    void splitsAtTheFirstColon() {
        assertEquals(Optional.of(new HomeUserId("realm", "user:1")), HomeUserId.parse("realm:user:1"));
    }

    @Test
    void rejectsAnIdWithoutBothParts() {
        assertEquals(Optional.empty(), HomeUserId.parse(""));
        assertEquals(Optional.empty(), HomeUserId.parse("user-1"));
        assertEquals(Optional.empty(), HomeUserId.parse(":user-1"));
        assertEquals(Optional.empty(), HomeUserId.parse("realm-1:"));
    }

    @Test
    void joinsTheParts() {
        assertEquals("realm-1:user-1", new HomeUserId("realm-1", "user-1").externalId());
    }
}
