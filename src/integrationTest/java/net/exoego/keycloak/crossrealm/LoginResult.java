package net.exoego.keycloak.crossrealm;

/** Where a login ended. It is either a redirect to the client with a code, or a page of Keycloak. */
final class LoginResult {

    private final String code;
    private final boolean loginForm;
    private final boolean chooser;
    private final String error;

    LoginResult(String code, boolean loginForm, boolean chooser, String error) {
        this.code = code;
        this.loginForm = loginForm;
        this.chooser = chooser;
        this.error = error;
    }

    /** The authorization code. Fails the test when the login ended on a page. */
    String code() {
        if (code == null) {
            throw new AssertionError("The login got no code. It ended on a page with the error: '" + error + "'");
        }
        return code;
    }

    boolean isLoginForm() {
        return loginForm;
    }

    /** The page lists the realms of the matches. */
    boolean isChooser() {
        return chooser;
    }

    /** The error text of the login form or of the error page. It is empty when the page shows none. */
    String error() {
        return error;
    }
}
