package net.exoego.keycloak.crossrealm;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingMatchesTest {

    @Test
    void keepsTheOrder() {
        PendingMatches matches = new PendingMatches(List.of(new HomeUserId("r2", "u2"), new HomeUserId("r1", "u1")));

        assertEquals(matches, PendingMatches.decode(matches.encode()));
    }

    @Test
    void keepsAnyCharacterOfAnId() {
        PendingMatches matches = new PendingMatches(List.of(
                new HomeUserId("a:b,c", "éあ😀 :,"),
                new HomeUserId("", "="),
                new HomeUserId("x", "y")));

        assertEquals(matches, PendingMatches.decode(matches.encode()));
    }

    @Test
    void noMatchesGiveAnEmptyNote() {
        assertEquals("", new PendingMatches(List.of()).encode());
        assertEquals(new PendingMatches(List.of()), PendingMatches.decode(""));
        assertEquals(new PendingMatches(List.of()), PendingMatches.decode(null));
    }
}
