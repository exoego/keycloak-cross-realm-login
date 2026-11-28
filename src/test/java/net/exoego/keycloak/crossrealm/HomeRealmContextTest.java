package net.exoego.keycloak.crossrealm;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakTransaction;
import org.keycloak.models.KeycloakTransactionManager;
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
    private String failsFor;
    private final List<KeycloakTransaction> afterCompletion = new ArrayList<>();
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
    void putsTheOldRealmBackWhenTheSwitchFails() {
        failsFor = "home-a";

        assertThrows(IllegalStateException.class, () -> HomeRealmContext.call(session, homeA, () -> "x"));

        assertSame(entry, current);
    }

    @Test
    void leavesNoContextRealmWhenThereWasNone() {
        current = null;

        HomeRealmContext.call(session, homeA, () -> {
            assertSame(homeA, current);
            return null;
        });

        assertNull(current);
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
    void runsTheRunnableInTheHomeRealmAndPutsTheOldRealmBack() {
        HomeRealmContext.run(session, homeA, () -> assertSame(homeA, current));

        assertSame(entry, current);
    }

    @Test
    void switchesToTheHomeRealmBeforeAndBackAfterTheWorkQueuedByTheBlock() {
        List<String> realms = new ArrayList<>();

        HomeRealmContext.callIncludingAfterCompletion(session, homeA, () -> {
            afterCompletion.add(work(() -> realms.add("work:" + current)));
            return null;
        });

        assertSame(entry, current);
        current = null;
        commitAll();
        assertEquals(List.of("work:home-a"), realms);
        assertSame(entry, current);
    }

    @Test
    void queuesTheSwitchBackWhenTheBlockThrows() {
        assertThrows(IllegalStateException.class, () -> HomeRealmContext.callIncludingAfterCompletion(session, homeA, () -> {
            throw new IllegalStateException();
        }));

        assertEquals(2, afterCompletion.size());
        current = homeB;
        commitAll();
        assertSame(entry, current);
    }

    @Test
    void queuesNoRealmSwitchBackWhenThereWasNoRealm() {
        current = null;

        HomeRealmContext.callIncludingAfterCompletion(session, homeA, () -> null);
        commitAll();

        assertNull(current);
    }

    @Test
    void restoresEachLevelOfNestedCallsAfterCompletion() {
        List<String> realms = new ArrayList<>();
        HomeRealmContext.callIncludingAfterCompletion(session, homeA, () -> {
            afterCompletion.add(work(() -> realms.add("a1:" + current)));
            HomeRealmContext.callIncludingAfterCompletion(session, homeB, () -> {
                afterCompletion.add(work(() -> realms.add("b:" + current)));
                return null;
            });
            afterCompletion.add(work(() -> realms.add("a2:" + current)));
            return null;
        });

        commitAll();

        assertEquals(List.of("a1:home-a", "b:home-b", "a2:home-a"), realms);
        assertSame(entry, current);
    }

    @Test
    void failsFastWithoutARunnable() {
        assertEquals("block",
                assertThrows(NullPointerException.class, () -> HomeRealmContext.run(session, homeA, null)).getMessage());
        assertEquals(List.of(), seen);
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

    private void commitAll() {
        for (KeycloakTransaction transaction : afterCompletion) {
            transaction.begin();
            transaction.commit();
        }
    }

    private static KeycloakTransaction work(Runnable work) {
        return new AbstractKeycloakTransaction() {
            @Override
            protected void commitImpl() {
                work.run();
            }

            @Override
            protected void rollbackImpl() {
            }
        };
    }

    private KeycloakSession session() {
        KeycloakTransactionManager transactions = (KeycloakTransactionManager) Proxy.newProxyInstance(
                KeycloakTransactionManager.class.getClassLoader(), new Class<?>[] {KeycloakTransactionManager.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("enlistAfterCompletion")) {
                        afterCompletion.add((KeycloakTransaction) args[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        KeycloakContext context = (KeycloakContext) Proxy.newProxyInstance(KeycloakContext.class.getClassLoader(),
                new Class<?>[] {KeycloakContext.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getRealm" -> current;
                    case "setRealm" -> {
                        current = (RealmModel) args[0];
                        String name = String.valueOf(current);
                        seen.add(name);
                        if (name.equals(failsFor)) {
                            throw new IllegalStateException(name);
                        }
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return (KeycloakSession) Proxy.newProxyInstance(KeycloakSession.class.getClassLoader(),
                new Class<?>[] {KeycloakSession.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getContext")) {
                        return context;
                    }
                    if (method.getName().equals("getTransactionManager")) {
                        return transactions;
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
