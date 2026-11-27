package net.exoego.keycloak.crossrealm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultCandidateLookupTest {

    private final DefaultCandidateLookup lookup = new DefaultCandidateLookup(null, Settings.of(null));

    @Test
    void failsWithoutAnEntryRealm() {
        assertThrows(NullPointerException.class, () -> lookup.findByIdentifier(null, "alice"));
        assertThrows(NullPointerException.class, () -> lookup.find(null, "realm", "user"));
    }
}
