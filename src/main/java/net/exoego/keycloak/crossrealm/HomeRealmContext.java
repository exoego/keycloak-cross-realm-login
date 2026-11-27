package net.exoego.keycloak.crossrealm;

import java.util.Objects;
import java.util.function.Supplier;

import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

/**
 * The only place that sets the context realm.
 * Some parts of Keycloak read the context realm and not the realm of the user.
 * An architecture test enforces that nothing else sets it.
 */
final class HomeRealmContext {

    private HomeRealmContext() {
    }

    /**
     * Runs the block with the home realm as the context realm.
     * The old realm is back afterwards, also when the block throws.
     * Calls can be nested.
     */
    static <T> T call(KeycloakSession session, RealmModel homeRealm, Supplier<T> block) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(homeRealm, "homeRealm");
        Objects.requireNonNull(block, "block");
        KeycloakContext context = session.getContext();
        RealmModel previous = context.getRealm();
        context.setRealm(homeRealm);
        try {
            return block.get();
        } finally {
            context.setRealm(previous);
        }
    }
}
