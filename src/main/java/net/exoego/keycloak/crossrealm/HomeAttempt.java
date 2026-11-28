package net.exoego.keycloak.crossrealm;

record HomeAttempt(Candidate candidate, boolean passwordValid, boolean enabled, String lockoutError) {

    boolean matches() {
        return passwordValid && enabled && lockoutError == null;
    }

    boolean countsAsFailure() {
        return lockoutError == null && !passwordValid;
    }
}
