package net.exoego.keycloak.crossrealm;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasskeyLoginIT {

    private static final String CLIENT = "passkey-client";
    private static final String PASSWORD = "the-password";

    private final String username = "passkey-" + UUID.randomUUID();

    @Test
    void aUserRegistersAPasskeyAndLogsInWithOnlyThePasskey() throws Exception {
        String host = URI.create(KeycloakEnvironment.realmUrl("master")).getHost();
        assertEquals("localhost", host, "A passkey needs the host name localhost, because an IP address cannot be a relying party ID");
        try (TempRealm home = new TempRealm(); Browser browser = new Browser()) {
            home.addPublicClient(CLIENT);
            home.allowPasskeys();
            String userId = home.addUser(username, PASSWORD, true);
            home.setName(userId, "Pass", "Key");
            home.requireAction(userId, "webauthn-register-passwordless");
            Browser.VirtualAuthenticator authenticator = browser.addVirtualAuthenticator();

            browser.loginAndRegisterPasskey(home.name(), CLIENT, username, PASSWORD).code();

            List<JsonObject> credentials = authenticator.credentials();
            assertEquals(1, credentials.size());
            JsonObject credential = credentials.get(0);
            assertEquals("localhost", credential.get("rpId").getAsString());
            assertTrue(credential.get("isResidentCredential").getAsBoolean());
            String userHandle = new String(Base64.getDecoder().decode(credential.get("userHandle").getAsString()), StandardCharsets.UTF_8);
            assertEquals(userId, userHandle);

            browser.clearCookies();
            assertTrue(browser.openLoginPage(home.name(), CLIENT).isLoginForm());

            String code = browser.loginWithPasskey(home.name(), CLIENT).code();
            IdToken token = browser.exchange(home.name(), CLIENT, code);
            assertEquals(username, token.preferredUsername());
            assertEquals(KeycloakEnvironment.realmUrl(home.name()), token.issuer());
        }
    }
}
