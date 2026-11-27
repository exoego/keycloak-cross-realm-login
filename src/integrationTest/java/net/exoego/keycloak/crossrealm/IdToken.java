package net.exoego.keycloak.crossrealm;

import java.util.Map;

/** The claims of an ID token. The signature is not checked. */
record IdToken(Map<String, Object> claims) {

    String issuer() {
        return String.valueOf(claims.get("iss"));
    }

    String subject() {
        return String.valueOf(claims.get("sub"));
    }

    String email() {
        return String.valueOf(claims.get("email"));
    }

    String preferredUsername() {
        return String.valueOf(claims.get("preferred_username"));
    }
}
