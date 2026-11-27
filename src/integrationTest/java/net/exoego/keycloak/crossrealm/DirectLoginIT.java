package net.exoego.keycloak.crossrealm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectLoginIT {

    @Test
    void aliceLogsInAtHomeA() throws Exception {
        try (Browser browser = new Browser()) {
            LoginResult result = browser.login("home-a", "test-client", "alice", "alice-password-a");
            IdToken token = browser.exchange("home-a", "test-client", result.code());

            assertEquals("alice", token.preferredUsername());
            assertEquals(KeycloakEnvironment.realmUrl("home-a"), token.issuer());
        }
    }

    @Test
    void aliceCannotLogInAtHomeBWithThePasswordOfHomeA() throws Exception {
        try (Browser browser = new Browser()) {
            LoginResult result = browser.login("home-b", "test-client", "alice", "alice-password-a");

            assertTrue(result.isLoginForm());
            assertEquals("Invalid username or password.", result.error());
        }
    }

    @Test
    void browsersDoNotShareCookies() throws Exception {
        try (Browser aliceBrowser = new Browser(); Browser bobBrowser = new Browser()) {
            String aliceCode = aliceBrowser.login("home-a", "test-client", "alice", "alice-password-a").code();
            String bobCode = bobBrowser.login("home-a", "test-client", "bob", "bob-password").code();

            assertEquals("alice", aliceBrowser.exchange("home-a", "test-client", aliceCode).preferredUsername());
            assertEquals("bob", bobBrowser.exchange("home-a", "test-client", bobCode).preferredUsername());
        }
    }

    @Test
    void theSsoCookieSkipsThePasswordInTheSameBrowserOnly() throws Exception {
        try (Browser aliceBrowser = new Browser(); Browser otherBrowser = new Browser()) {
            aliceBrowser.login("home-a", "test-client", "alice", "alice-password-a").code();

            LoginResult again = aliceBrowser.login("home-a", "test-client", "unused", "unused");
            assertEquals("alice", aliceBrowser.exchange("home-a", "test-client", again.code()).preferredUsername());

            otherBrowser.open("home-a", "test-client");
            assertEquals(true, otherBrowser.evaluate("document.querySelector('form#kc-form-login') !== null"));
        }
    }

    @Test
    void theBrowserIsHeadlessChromeThatRunsJavaScript() throws Exception {
        try (Browser browser = new Browser()) {
            browser.open("home-a", "test-client");
            assertEquals(42, browser.evaluate("6 * 7"));
            String userAgent = (String) browser.evaluate("navigator.userAgent");
            assertTrue(userAgent.contains(Browser.HEADED ? "Chrome/" : "HeadlessChrome/"), userAgent);
        }
    }
}
