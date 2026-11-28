package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.EventRepresentation;

import static net.exoego.keycloak.crossrealm.Logins.CLIENT;
import static net.exoego.keycloak.crossrealm.Logins.assertRejected;
import static net.exoego.keycloak.crossrealm.Logins.proxyId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HomeRealmBruteForceIT {

    private static final String ENTRY = "entry";
    private static final String PASSWORD = "the-password";
    private static final String WRONG = "not-the-password";

    private final String username = "guard-" + UUID.randomUUID();

    @Test
    void wrongPasswordsAtTheEntryRealmLockTheUserInTheHomeRealm() {
        try (TempRealm home = protectedHome(2); Browser browser = new Browser()) {
            String userId = home.addUser(username, PASSWORD, true);

            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));
            Await.until("one failure", () -> home.numFailures(userId) == 1);
            assertFalse(home.isLockedOut(userId));

            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));
            Await.until("a lockout", () -> home.isLockedOut(userId));
            assertEquals(2, home.numFailures(userId));
        }
    }

    @Test
    void aLockedOutUserWithTheRightPasswordGetsTheSamePageAsAWrongPassword() {
        try (TempRealm home = protectedHome(2); Browser browser = new Browser()) {
            String userId = home.addUser(username, PASSWORD, true);
            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));
            String wrongShape = browser.loginFormShape();
            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));
            Await.until("a lockout", () -> home.isLockedOut(userId));

            assertRejected(browser.login(ENTRY, CLIENT, username, PASSWORD));
            String lockedShape = browser.loginFormShape();

            assertEquals(wrongShape, lockedShape);
        }
    }

    @Test
    void aPermanentlyLockedOutUserWithTheRightPasswordIsRejected() {
        try (TempRealm home = protectedHome(2); Browser browser = new Browser()) {
            home.lockOutPermanently();
            String userId = home.addUser(username, PASSWORD, true);
            lockOut(home, userId, browser);

            assertRejected(browser.login(ENTRY, CLIENT, username, PASSWORD));
        }
    }

    @Test
    void aLockedOutAttemptWritesTheTemporaryLockoutErrorAndCountsNoFailure() {
        try (TempRealm home = protectedHome(2); Browser browser = new Browser()) {
            String userId = home.addUser(username, PASSWORD, true);
            lockOut(home, userId, browser);

            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));

            assertEquals(List.of("invalid_user_credentials", "invalid_user_credentials", "user_temporarily_disabled"),
                    errors(home, userId));
            Await.staysTrue("two failures", () -> home.numFailures(userId) == 2);
        }
    }

    @Test
    void aLockedOutAttemptWritesThePermanentLockoutError() {
        try (TempRealm home = protectedHome(2); Browser browser = new Browser()) {
            home.lockOutPermanently();
            String userId = home.addUser(username, PASSWORD, true);
            lockOut(home, userId, browser);

            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));

            assertEquals(List.of("invalid_user_credentials", "invalid_user_credentials", "user_disabled"),
                    errors(home, userId));
        }
    }

    @Test
    void aSuccessResetsTheFailureCountOfTheOnlyMatch() throws Exception {
        try (TempRealm home = protectedHome(5); Browser browser = new Browser()) {
            String userId = home.addUser(username, PASSWORD, true);
            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));
            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));
            Await.until("two failures", () -> home.numFailures(userId) == 2);

            IdToken token = browser.exchange(ENTRY, CLIENT, browser.login(ENTRY, CLIENT, username, PASSWORD).code());

            assertEquals(proxyId(ENTRY, home.id(), userId), token.subject());
            Await.until("no failure", () -> home.numFailures(userId) == 0);
            assertEquals(List.of("invalid_user_credentials", "invalid_user_credentials"), errors(home, userId));
        }
    }

    @Test
    void aSuccessResetsTheFailureCountOfTheChosenUserOnly() throws Exception {
        try (TempRealm alpha = protectedHome(5); TempRealm bravo = protectedHome(5); Browser browser = new Browser()) {
            alpha.setDisplayName("alpha");
            bravo.setDisplayName("bravo");
            String alphaId = alpha.addUser(username, PASSWORD, true);
            String bravoId = bravo.addUser(username, PASSWORD, true);
            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));
            Await.until("failures in both realms",
                    () -> alpha.numFailures(alphaId) == 1 && bravo.numFailures(bravoId) == 1);

            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());
            IdToken token = browser.exchange(ENTRY, CLIENT, browser.choose(1).code());

            assertEquals(proxyId(ENTRY, bravo.id(), bravoId), token.subject());
            Await.until("no failure in the chosen realm", () -> bravo.numFailures(bravoId) == 0);
            Await.staysTrue("the failure of the other realm stays", () -> alpha.numFailures(alphaId) == 1);
        }
    }

    @Test
    void theChooserRejectsAChoiceThatGotLockedOutMeanwhile() {
        try (TempRealm alpha = protectedHome(2); TempRealm bravo = protectedHome(5);
                Browser browser = new Browser(); Browser attacker = new Browser()) {
            alpha.setDisplayName("alpha");
            bravo.setDisplayName("bravo");
            String alphaId = alpha.addUser(username, PASSWORD, true);
            bravo.addUser(username, PASSWORD, true);
            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());

            lockOut(alpha, alphaId, attacker);

            assertRejected(browser.choose(0));
            assertEquals(2, alpha.numFailures(alphaId));
            assertEquals(List.of("invalid_user_credentials", "invalid_user_credentials", "user_temporarily_disabled"),
                    errors(alpha, alphaId));
        }
    }

    @Test
    void aChoiceThatGotDisabledMeanwhileWritesTheDisabledError() {
        try (TempRealm alpha = protectedHome(5); TempRealm bravo = protectedHome(5); Browser browser = new Browser()) {
            alpha.setDisplayName("alpha");
            bravo.setDisplayName("bravo");
            String alphaId = alpha.addUser(username, PASSWORD, true);
            bravo.addUser(username, PASSWORD, true);
            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());

            alpha.disableUser(alphaId);

            assertRejected(browser.choose(0));
            assertEquals(List.of("user_disabled"), errors(alpha, alphaId));
            Await.staysTrue("no failure", () -> alpha.numFailures(alphaId) == 0);
        }
    }

    @Test
    void aLockedOutCandidateNeverMatchesWhileTheOtherOneLogsIn() throws Exception {
        try (TempRealm locked = protectedHome(2); TempRealm free = protectedHome(5);
                Browser browser = new Browser(); Browser attacker = new Browser()) {
            String lockedId = locked.addUser(username, PASSWORD, true);
            String freeId = free.addUser(username, PASSWORD, true);
            lockOut(locked, lockedId, attacker);

            LoginResult result = browser.login(ENTRY, CLIENT, username, PASSWORD);

            assertFalse(result.isChooser());
            assertEquals(proxyId(ENTRY, free.id(), freeId), browser.exchange(ENTRY, CLIENT, result.code()).subject());
        }
    }

    @Test
    void aRightLoginOfOneRealmCountsNoFailureAndWritesNoEventForTheOther() throws Exception {
        try (TempRealm one = protectedHome(5); TempRealm other = protectedHome(5); Browser browser = new Browser()) {
            String oneId = one.addUser(username, "password-of-one", true);
            String otherId = other.addUser(username, "password-of-other", true);
            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));
            Await.until("failures in both realms", () -> one.numFailures(oneId) == 1 && other.numFailures(otherId) == 1);

            IdToken token = browser.exchange(ENTRY, CLIENT,
                    browser.login(ENTRY, CLIENT, username, "password-of-one").code());

            assertEquals(proxyId(ENTRY, one.id(), oneId), token.subject());
            Await.until("a reset in the realm of the login", () -> one.numFailures(oneId) == 0);
            assertEquals(1, other.loginErrors(otherId).size());
            Await.staysTrue("the failure of the other realm stays", () -> other.numFailures(otherId) == 1);
        }
    }

    @Test
    void whenNobodyMatchesEachCandidateGetsAFailureAndAnEvent() {
        try (TempRealm one = protectedHome(5); TempRealm other = protectedHome(5); Browser browser = new Browser()) {
            String oneId = one.addUser(username, "password-of-one", true);
            String otherId = other.addUser(username, "password-of-other", true);

            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));

            Await.until("a failure in each realm", () -> one.numFailures(oneId) == 1 && other.numFailures(otherId) == 1);
            assertEventOf(one, oneId);
            assertEventOf(other, otherId);
        }
    }

    @Test
    void theEventHasTheEntryRealmAndTheIdentifierAsDetails() {
        try (TempRealm home = protectedHome(5); Browser browser = new Browser()) {
            String userId = home.addUser(username, PASSWORD, true);

            assertRejected(browser.login(ENTRY, CLIENT, " " + username + " ", WRONG));

            EventRepresentation event = home.loginErrors(userId).get(0);
            assertEquals(username, event.getDetails().get("username"));
            assertEquals(ENTRY, event.getDetails().get("entry_realm"));
        }
    }

    @Test
    void aHomeRealmWithoutBruteForceProtectionCountsNothing() {
        try (TempRealm home = new TempRealm(); Browser browser = new Browser()) {
            home.enableEvents();
            String userId = home.addUser(username, PASSWORD, true);

            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));

            assertEquals(List.of("invalid_user_credentials"), errors(home, userId));
            home.protectFromBruteForce(5);
            Await.staysTrue("no failure", () -> home.numFailures(userId) == 0);
        }
    }

    @Test
    void aDisabledUserWithAWrongPasswordGetsAFailure() {
        try (TempRealm home = protectedHome(5); Browser browser = new Browser()) {
            String userId = home.addUser(username, PASSWORD, false);

            assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));

            Await.until("one failure", () -> home.numFailures(userId) == 1);
            assertEquals(List.of("invalid_user_credentials"), errors(home, userId));
        }
    }

    @Test
    void aDisabledUserWithTheRightPasswordGetsNoFailure() {
        try (TempRealm home = protectedHome(5); Browser browser = new Browser()) {
            String userId = home.addUser(username, PASSWORD, false);

            assertRejected(browser.login(ENTRY, CLIENT, username, PASSWORD));

            assertEquals(List.of("user_disabled"), errors(home, userId));
            Await.staysTrue("no failure", () -> home.numFailures(userId) == 0);
        }
    }

    private static TempRealm protectedHome(int failureFactor) {
        TempRealm home = new TempRealm();
        home.protectFromBruteForce(failureFactor);
        home.enableEvents();
        return home;
    }

    private void lockOut(TempRealm home, String userId, Browser browser) {
        assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));
        assertRejected(browser.login(ENTRY, CLIENT, username, WRONG));
        Await.until("a lockout", () -> home.isLockedOut(userId));
    }

    private static List<String> errors(TempRealm home, String userId) {
        return home.loginErrors(userId).stream()
                .sorted((a, b) -> Long.compare(a.getTime(), b.getTime()))
                .map(EventRepresentation::getError)
                .toList();
    }

    private static void assertEventOf(TempRealm home, String userId) {
        List<EventRepresentation> events = home.loginErrors(userId);
        assertEquals(1, events.size());
        EventRepresentation event = events.get(0);
        assertEquals("LOGIN_ERROR", event.getType());
        assertEquals("invalid_user_credentials", event.getError());
        assertEquals(userId, event.getUserId());
        assertEquals(home.id(), event.getRealmId());
        assertNull(event.getClientId());
        assertNotNull(event.getIpAddress());
        assertFalse(event.getIpAddress().isBlank());
        assertEquals(ENTRY, event.getDetails().get("entry_realm"));
    }
}
