package net.exoego.keycloak.crossrealm;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.EventRepresentation;

import static net.exoego.keycloak.crossrealm.Logins.CLIENT;
import static net.exoego.keycloak.crossrealm.Logins.assertRejected;
import static net.exoego.keycloak.crossrealm.Logins.proxyId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossRealmPasskeyIT {

    private static final String HOME_CLIENT = "home-client";
    private static final String PASSWORD = "the-password";
    private static final String WRONG = "not-the-password";
    private static final String PASSKEY_ERROR = "Passkey authentication result is invalid.";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String username = "passkey-" + UUID.randomUUID();

    @Test
    void aPasskeyOfTheHomeRealmLogsInAtTheEntryRealmAndMovesTheCounterOfTheHomeRealm() throws Exception {
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage(); TempRealm home = new TempRealm();
                Browser browser = new Browser()) {
            entry.allowPasskeys();
            String userId = registerPasskey(home, browser);
            long before = counter(home, userId);

            IdToken token = browser.exchange(entry.name(), CLIENT, browser.loginWithPasskey(entry.name(), CLIENT).code());

            assertEquals(proxyId(entry.name(), home.id(), userId), token.subject());
            assertEquals(KeycloakEnvironment.realmUrl(entry.name()), token.issuer());
            assertTrue(counter(home, userId) > before);
        }
    }

    @Test
    void aHomeRealmThatRequiresUserVerificationRejectsAPasskeyUsedWithoutIt() {
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage(); TempRealm home = new TempRealm();
                Browser browser = new Browser()) {
            home.enableEvents();
            entry.allowPasskeys();
            entry.setPasskeyUserVerification("discouraged");
            String userId = registerPasskey(home, browser);
            home.setPasskeyUserVerification("required");
            long before = counter(home, userId);

            LoginResult result = browser.loginWithPasskey(entry.name(), CLIENT);

            assertTrue(result.isLoginForm());
            assertEquals(PASSKEY_ERROR, result.error());
            assertEquals(List.of("invalid_user_credentials"), errors(home, userId));
            assertEquals(before, counter(home, userId));
        }
    }

    @Test
    void aLockedOutHomeUserCannotLogInWithAPasskey() {
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage(); TempRealm home = new TempRealm();
                Browser browser = new Browser(); Browser attacker = new Browser()) {
            home.protectFromBruteForce(2);
            home.enableEvents();
            entry.allowPasskeys();
            String userId = registerPasskey(home, browser);
            assertRejected(attacker.login(entry.name(), CLIENT, username, WRONG));
            assertRejected(attacker.login(entry.name(), CLIENT, username, WRONG));
            Await.until("a lockout", () -> home.isLockedOut(userId));
            long before = counter(home, userId);

            LoginResult result = browser.loginWithPasskey(entry.name(), CLIENT);

            assertTrue(result.isLoginForm());
            assertEquals(PASSKEY_ERROR, result.error());
            assertEquals(List.of("invalid_user_credentials", "invalid_user_credentials", "user_temporarily_disabled"),
                    errors(home, userId));
            Await.staysTrue("two failures", () -> home.numFailures(userId) == 2);
            assertEquals(before, counter(home, userId));
        }
    }

    private String registerPasskey(TempRealm home, Browser browser) {
        home.addPublicClient(HOME_CLIENT);
        home.allowPasskeys();
        String userId = home.addUser(username, PASSWORD, true);
        home.setName(userId, "Pass", "Key");
        home.requireAction(userId, "webauthn-register-passwordless");
        browser.addVirtualAuthenticator();
        browser.loginAndRegisterPasskey(home.name(), HOME_CLIENT, username, PASSWORD).code();
        browser.clearCookies();
        return userId;
    }

    private static long counter(TempRealm home, String userId) {
        return home.resource().users().get(userId).credentials().stream()
                .filter(credential -> "webauthn-passwordless".equals(credential.getType()))
                .map(CrossRealmPasskeyIT::counterOf)
                .findFirst().orElseThrow();
    }

    private static long counterOf(CredentialRepresentation credential) {
        try {
            return JSON.readTree(credential.getCredentialData()).get("counter").asLong();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> errors(TempRealm home, String userId) {
        return home.loginErrors(userId).stream()
                .sorted(Comparator.comparingLong(EventRepresentation::getTime))
                .map(EventRepresentation::getError)
                .toList();
    }
}
