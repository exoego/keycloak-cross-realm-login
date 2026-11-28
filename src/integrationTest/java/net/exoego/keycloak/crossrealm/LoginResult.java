package net.exoego.keycloak.crossrealm;

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

    String code() {
        if (code == null) {
            throw new AssertionError("The login got no code. It ended on a page with the error: '" + error + "'");
        }
        return code;
    }

    boolean isLoginForm() {
        return loginForm;
    }

    boolean isChooser() {
        return chooser;
    }

    String error() {
        return error;
    }
}
