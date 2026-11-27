package net.exoego.keycloak.crossrealm;

/** Where a login ended. It is either a redirect to the client with a code, or a page of Keycloak. */
final class LoginResult {

    private final String code;
    private final boolean loginForm;
    private final String error;
    private final String formShape;

    LoginResult(String code, boolean loginForm, String error, String formShape) {
        this.code = code;
        this.loginForm = loginForm;
        this.error = error;
        this.formShape = formShape;
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

    /** The error text of the login form or of the error page. It is empty when the page shows none. */
    String error() {
        return error;
    }

    /** The markup of the login form without the values that change for each request. It is empty without a form. */
    String formShape() {
        return formShape;
    }
}
