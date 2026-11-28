package net.exoego.keycloak.crossrealm;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.keycloak.models.RealmModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultCandidateLookupTest {

    private final DefaultCandidateLookup lookup = new DefaultCandidateLookup(null, Settings.of(null));

    @Test
    void failsWithoutAnEntryRealm() {
        assertEquals("entryRealm",
                assertThrows(NullPointerException.class, () -> lookup.findByIdentifier(null, "alice")).getMessage());
        assertEquals("entryRealm",
                assertThrows(NullPointerException.class, () -> lookup.find(null, "realm", "user")).getMessage());
    }

    @Test
    void searchesNothingForAnIdentifierThatNoUserCanHave() {
        RealmModel entryRealm = realmThatFailsWhenRead();

        assertEquals(List.of(), lookup.findByIdentifier(entryRealm, null));
        assertEquals(List.of(), lookup.findByIdentifier(entryRealm, "   "));
        assertEquals(List.of(), lookup.findByIdentifier(entryRealm, "a".repeat(256)));
        assertThrows(AssertionError.class, () -> lookup.findByIdentifier(entryRealm, "a".repeat(255)));
    }

    @Test
    void looksUpNoUserIdThatNoUserCanHave() {
        RealmModel entryRealm = realmThatFailsWhenRead();

        assertEquals(Optional.empty(), lookup.findByUserId(entryRealm, null));
        assertEquals(Optional.empty(), lookup.findByUserId(entryRealm, ""));
        assertEquals(Optional.empty(), lookup.findByUserId(entryRealm, "a\u0000b"));
        assertThrows(NullPointerException.class, () -> lookup.findByUserId(entryRealm, "ab"));
    }

    @Test
    void comparesTheRowsAgainAsKeycloakDoes() {
        List<Object[]> named = row("realm", "a", "rene@example.com");
        List<Object[]> withAccent = row("realm", "b", "ren\u00e9@example.com", true, null);

        assertNull(DefaultCandidateLookup.match("jose", null, row("realm", "c", "jos\u00e9")));
        assertEquals("a", DefaultCandidateLookup.match("rene@example.com", withAccent, named));
        assertNull(DefaultCandidateLookup.match("rene@example.com", withAccent, null));
        assertEquals("b", DefaultCandidateLookup.match("ren\u00e9@example.com", withAccent, null));
    }

    private static List<Object[]> row(Object... columns) {
        return Collections.singletonList(columns);
    }

    private static RealmModel realmThatFailsWhenRead() {
        return (RealmModel) Proxy.newProxyInstance(RealmModel.class.getClassLoader(), new Class<?>[] {RealmModel.class},
                (proxy, method, args) -> {
                    throw new AssertionError(method.getName());
                });
    }
}
