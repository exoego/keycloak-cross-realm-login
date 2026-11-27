package net.exoego.keycloak.crossrealm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectLoginIT {

    @Test
    void aliceLogsInAtHomeA() throws Exception {
        Browser browser = new Browser();

        LoginResult result = browser.login("home-a", "test-client", "alice", "alice-password-a");
        IdToken token = browser.exchange("home-a", "test-client", result.code());

        assertEquals("alice", token.preferredUsername());
        assertEquals(KeycloakEnvironment.realmUrl("home-a"), token.issuer());
    }

    @Test
    void aliceCannotLogInAtHomeBWithThePasswordOfHomeA() throws Exception {
        LoginResult result = new Browser().login("home-b", "test-client", "alice", "alice-password-a");

        assertTrue(result.isLoginForm());
        assertEquals("Invalid username or password.", result.error());
    }

    @Test
    void browsersDoNotShareCookies() throws Exception {
        Browser aliceBrowser = new Browser();
        Browser bobBrowser = new Browser();

        String aliceCode = aliceBrowser.login("home-a", "test-client", "alice", "alice-password-a").code();
        String bobCode = bobBrowser.login("home-a", "test-client", "bob", "bob-password").code();

        assertEquals("alice", aliceBrowser.exchange("home-a", "test-client", aliceCode).preferredUsername());
        assertEquals("bob", bobBrowser.exchange("home-a", "test-client", bobCode).preferredUsername());
    }
}
