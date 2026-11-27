package net.exoego.keycloak.crossrealm;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsTest {

    @Test
    void splitsTheIdsAtCommas() {
        assertEquals(Set.of("a", "b"), Settings.of("a,b").excludedRealmIds());
    }

    @Test
    void trimsTheIdsAndIgnoresBlankEntries() {
        assertEquals(Set.of("a", "b"), Settings.of(" a, b ,,  , a ").excludedRealmIds());
    }

    @Test
    void excludesNoRealmWithoutTheSetting() {
        assertEquals(Set.of(), Settings.of(null).excludedRealmIds());
        assertEquals(Set.of(), Settings.of("").excludedRealmIds());
        assertEquals(Set.of(), Settings.of(" , ").excludedRealmIds());
    }
}
