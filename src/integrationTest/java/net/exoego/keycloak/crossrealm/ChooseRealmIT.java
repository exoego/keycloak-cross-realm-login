package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static net.exoego.keycloak.crossrealm.Logins.CLIENT;
import static net.exoego.keycloak.crossrealm.Logins.assertRejected;
import static net.exoego.keycloak.crossrealm.Logins.proxyId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChooseRealmIT {

    private static final String ENTRY = "entry";
    private static final String PASSWORD = "same-password";

    private final String username = "twin-" + UUID.randomUUID();

    @Test
    void theChooserListsTheMatchesSortedByNameAndEachChoiceLogsInAsItsUser() throws Exception {
        try (TempRealm bravo = new TempRealm(); TempRealm alpha = new TempRealm(); TempRealm other = new TempRealm()) {
            bravo.setDisplayName("Bravo");
            alpha.setDisplayName("alpha");
            other.setDisplayName("Other");
            String bravoUser = bravo.addUser(username, PASSWORD, true);
            String alphaUser = alpha.addUser(username, PASSWORD, true);
            other.addUser(username, "another-password", true);

            try (Browser browser = new Browser()) {
                LoginResult chooser = browser.login(ENTRY, CLIENT, username, PASSWORD);
                assertTrue(chooser.isChooser());
                assertEquals(List.of(new Browser.Choice("alpha", ""), new Browser.Choice("Bravo", "")),
                        browser.choices());
                assertEquals("Choose where to sign in", browser.title());
                IdToken token = browser.exchange(ENTRY, CLIENT, browser.choose(0).code());
                assertEquals(proxyId(ENTRY, alpha.id(), alphaUser), token.subject());
            }
            try (Browser browser = new Browser()) {
                browser.login(ENTRY, CLIENT, username, PASSWORD);
                IdToken token = browser.exchange(ENTRY, CLIENT, browser.choose(1).code());
                assertEquals(proxyId(ENTRY, bravo.id(), bravoUser), token.subject());
            }
        }
    }

    @Test
    void aDisplayNameWithHtmlShowsAsText() {
        String script = "<img src=x onerror=alert(1)>";
        String bold = "<b>bold</b>";
        try (TempRealm one = new TempRealm(); TempRealm two = new TempRealm(); Browser browser = new Browser()) {
            one.setDisplayName(script);
            two.setDisplayName(bold);
            one.addUser(username, PASSWORD, true);
            two.addUser(username, PASSWORD, true);

            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());

            assertEquals(List.of(new Browser.Choice(bold, ""), new Browser.Choice(script, "")), browser.choices());
            assertEquals(0, browser.count("#cross-realm-choices img, #cross-realm-choices b"));
            assertTrue(browser.chooserHtml().contains("&lt;b&gt;bold&lt;/b&gt;"));
        }
    }

    @Test
    void aRealmWithoutDisplayNameShowsItsName() {
        try (TempRealm named = new TempRealm(); TempRealm unnamed = new TempRealm(); Browser browser = new Browser()) {
            named.setDisplayName("Zeta");
            named.addUser(username, PASSWORD, true);
            unnamed.setDisplayName("  ");
            unnamed.addUser(username, PASSWORD, true);

            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());

            assertEquals(List.of(new Browser.Choice(unnamed.name(), ""), new Browser.Choice("Zeta", "")),
                    browser.choices());
        }
    }

    @Test
    void realmsWithOneDisplayNameShowTheirRealmNames() {
        try (TempRealm one = new TempRealm(); TempRealm two = new TempRealm(); TempRealm third = new TempRealm();
                Browser browser = new Browser()) {
            one.setDisplayName("Acme");
            two.setDisplayName("Acme");
            third.setDisplayName("Other");
            one.addUser(username, PASSWORD, true);
            two.addUser(username, PASSWORD, true);
            third.addUser(username, PASSWORD, true);

            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());

            List<String> names = List.of(one.name(), two.name()).stream().sorted().toList();
            assertEquals(List.of(new Browser.Choice("Acme", names.get(0)), new Browser.Choice("Acme", names.get(1)),
                    new Browser.Choice("Other", "")), browser.choices());
        }
    }

    @Test
    void theChooserShowsTheIdentifierAndALinkToStartAgain() {
        try (TempRealm one = new TempRealm(); TempRealm two = new TempRealm(); Browser browser = new Browser()) {
            one.addUser(username, PASSWORD, true);
            two.addUser(username, PASSWORD, true);

            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());
            assertEquals(username, browser.attemptedUsername());

            LoginResult restarted = browser.restartLogin();
            assertTrue(restarted.isLoginForm());
            assertFalse(restarted.isChooser());
            assertNull(browser.attemptedUsername());
        }
    }

    @Test
    void reloadingTheFlowPageShowsTheLoginFormWithoutTheIdentifier() {
        try (TempRealm one = new TempRealm(); TempRealm two = new TempRealm(); Browser browser = new Browser()) {
            one.addUser(username, PASSWORD, true);
            two.addUser(username, PASSWORD, true);
            browser.login(ENTRY, CLIENT, username, PASSWORD);

            LoginResult reloaded = browser.reopenFlowPage();

            assertTrue(reloaded.isLoginForm());
            assertNull(browser.attemptedUsername());
        }
    }

    @Test
    void aForgedChoiceFailsLikeAWrongPassword() {
        try (TempRealm one = new TempRealm(); TempRealm two = new TempRealm()) {
            one.addUser(username, PASSWORD, true);
            two.addUser(username, PASSWORD, true);

            for (String value : List.of("2", "-1", "abc", "", "1.5", "99999999999")) {
                try (Browser browser = new Browser()) {
                    assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());

                    assertRejected(browser.chooseWithValue(0, value));
                }
            }
        }
    }

    @Test
    void aChoiceWithoutStoredMatchesFailsLikeAWrongPassword() {
        try (TempRealm one = new TempRealm(); Browser browser = new Browser()) {
            one.addUser(username, PASSWORD, true);
            browser.open(ENTRY, CLIENT);

            assertRejected(browser.postChoiceFromLoginForm("0"));
        }
    }

    @Test
    void aNewPasswordPostDropsTheStoredMatches() {
        try (TempRealm one = new TempRealm(); TempRealm two = new TempRealm(); Browser browser = new Browser()) {
            one.addUser(username, PASSWORD, true);
            two.addUser(username, PASSWORD, true);
            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());

            assertRejected(browser.postPasswordFromChooser(username, "wrong-password"));

            assertRejected(browser.postChoiceFromLoginForm("0"));
        }
    }

    @Test
    void aFailedChoiceDropsTheStoredMatches() {
        try (TempRealm one = new TempRealm(); TempRealm two = new TempRealm(); Browser browser = new Browser()) {
            one.addUser(username, PASSWORD, true);
            two.addUser(username, PASSWORD, true);
            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());
            assertRejected(browser.chooseWithValue(0, "9"));

            assertRejected(browser.postChoiceFromLoginForm("0"));
        }
    }

    @Test
    void aUserDisabledAfterThePasswordCheckCannotBeChosen() {
        try (TempRealm one = new TempRealm(); TempRealm two = new TempRealm(); Browser browser = new Browser()) {
            one.setDisplayName("A");
            two.setDisplayName("B");
            String userId = one.addUser(username, PASSWORD, true);
            two.addUser(username, PASSWORD, true);
            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());

            one.disableUser(userId);

            assertRejected(browser.choose(0));
        }
    }

    @Test
    void aRealmDisabledAfterThePasswordCheckCannotBeChosen() {
        try (TempRealm one = new TempRealm(); TempRealm two = new TempRealm(); Browser browser = new Browser()) {
            one.setDisplayName("A");
            two.setDisplayName("B");
            one.addUser(username, PASSWORD, true);
            two.addUser(username, PASSWORD, true);
            assertTrue(browser.login(ENTRY, CLIENT, username, PASSWORD).isChooser());

            one.disable();

            assertRejected(browser.choose(0));
        }
    }

    @Test
    void theChooserShowsJapaneseForABrowserInJapanese() {
        try (TempEntryRealm entry = TempEntryRealm.withUserStorage(); TempRealm one = new TempRealm();
                TempRealm two = new TempRealm(); Browser browser = new Browser("ja")) {
            entry.supportLocales("en", "ja");
            one.addUser(username, PASSWORD, true);
            two.addUser(username, PASSWORD, true);

            assertTrue(browser.login(entry.name(), CLIENT, username, PASSWORD).isChooser());

            assertEquals("ログイン先を選んでください", browser.title());
        }
    }
}
