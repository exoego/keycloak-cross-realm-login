package net.exoego.keycloak.crossrealm;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HomeRealmContextTest {

    private final RealmModel entry = realm("entry");
    private final RealmModel homeA = realm("home-a");
    private final RealmModel homeB = realm("home-b");
    private final List<String> seen = new ArrayList<>();
    private RealmModel current = entry;
    private final KeycloakSession session = session();

    @Test
    void runsTheBlockInTheHomeRealmAndPutsTheOldRealmBack() {
        String result = HomeRealmContext.call(session, homeA, () -> {
            assertSame(homeA, current);
            return "done";
        });

        assertEquals("done", result);
        assertSame(entry, current);
    }

    @Test
    void putsTheOldRealmBackAfterAnException() {
        IllegalStateException failure = new IllegalStateException("failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> HomeRealmContext.call(session, homeA, () -> {
            throw failure;
        }));

        assertSame(failure, thrown);
        assertSame(entry, current);
    }

    @Test
    void putsTheOldRealmBackAfterAnError() {
        assertThrows(AssertionError.class, () -> HomeRealmContext.call(session, homeA, () -> {
            throw new AssertionError("failed");
        }));

        assertSame(entry, current);
    }

    @Test
    void restoresEachLevelOfNestedCalls() {
        HomeRealmContext.call(session, homeA, () -> {
            HomeRealmContext.call(session, homeB, () -> {
                assertSame(homeB, current);
                return null;
            });
            assertSame(homeA, current);
            return null;
        });

        assertSame(entry, current);
        assertEquals(List.of("home-a", "home-b", "home-a", "entry"), seen);
    }

    @Test
    void restoresTheOuterRealmWhenTheInnerCallFails() {
        HomeRealmContext.call(session, homeA, () -> {
            assertThrows(IllegalStateException.class, () -> HomeRealmContext.call(session, homeB, () -> {
                throw new IllegalStateException();
            }));
            assertSame(homeA, current);
            return null;
        });

        assertSame(entry, current);
    }

    @Test
    void returnsNullFromTheBlock() {
        assertNull(HomeRealmContext.call(session, homeA, () -> null));
    }

    @Test
    void failsFastWithoutAHomeRealm() {
        assertEquals("homeRealm",
                assertThrows(NullPointerException.class, () -> HomeRealmContext.call(session, null, () -> "x")).getMessage());
        assertSame(entry, current);
        assertEquals(List.of(), seen);
    }

    @Test
    void failsFastWithoutASessionOrABlock() {
        assertEquals("session",
                assertThrows(NullPointerException.class, () -> HomeRealmContext.call(null, homeA, () -> "x")).getMessage());
        assertEquals("block",
                assertThrows(NullPointerException.class, () -> HomeRealmContext.call(session, homeA, null)).getMessage());
        assertEquals(List.of(), seen);
    }

    private KeycloakSession session() {
        KeycloakContext context = (KeycloakContext) Proxy.newProxyInstance(KeycloakContext.class.getClassLoader(),
                new Class<?>[] {KeycloakContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getRealm" -> current;
                    case "setRealm" -> {
                        current = (RealmModel) args[0];
                        seen.add(current.getName());
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (KeycloakSession) Proxy.newProxyInstance(KeycloakSession.class.getClassLoader(),
                new Class<?>[] {KeycloakSession.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getContext")) {
                        return context;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static RealmModel realm(String name) {
        return (RealmModel) Proxy.newProxyInstance(RealmModel.class.getClassLoader(), new Class<?>[] {RealmModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName", "toString" -> name;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
