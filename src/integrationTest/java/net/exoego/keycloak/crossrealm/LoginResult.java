package net.exoego.keycloak.crossrealm;

import java.net.URI;

import org.jsoup.nodes.Document;

/** Where a login ended. It is either a redirect to the client with a code, or a page of Keycloak. */
final class LoginResult {

    private static final String ERROR_SELECTOR = "#input-error, #kc-error-message, .kc-feedback-text";

    private final String code;
    private final Document page;

    LoginResult(String code, Document page) {
        this.code = code;
        this.page = page;
    }

    /** The authorization code. Fails the test when the login ended on a page. */
    String code() {
        if (code == null) {
            throw new AssertionError("The login got no code. It ended on a page with the error: '" + error() + "'");
        }
        return code;
    }

    URI formAction() {
        return URI.create(page.selectFirst("form#kc-form-login").absUrl("action"));
    }

    boolean isLoginForm() {
        return page != null && page.selectFirst("form#kc-form-login") != null;
    }

    /** The error text of the login form or of the error page. It is empty when the page shows none. */
    String error() {
        if (page == null) {
            return "";
        }
        var element = page.selectFirst(ERROR_SELECTOR);
        return element == null ? "" : element.text();
    }
}
