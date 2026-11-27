package net.exoego.keycloak.crossrealm;

import java.util.Objects;

import org.keycloak.credential.CredentialInput;
import org.keycloak.models.KeycloakSession;

/** Checks credentials of a candidate in the context of its home realm. */
public final class HomeRealmCredentials {

    private HomeRealmCredentials() {
    }

    /**
     * Returns whether the input is valid for the candidate.
     * A user without a stored credential of that type is not valid.
     */
    public static boolean isValid(KeycloakSession session, Candidate candidate, CredentialInput input) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(input, "input");
        // With the user cache, Keycloak 26.8 checks a password with the realm of the user.
        // Without the cache, and for a user of a user federation, it uses the context realm.
        // The home realm context makes the check independent of which case applies.
        // The credential manager of the user object is the one of the user cache.
        // One made by hand would skip the cache, and its changes would not invalidate the cached user.
        return HomeRealmContext.call(session, candidate.realm(),
                () -> candidate.user().credentialManager().isValid(input));
    }
}
