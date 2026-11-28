package net.exoego.keycloak.crossrealm;

import java.util.Objects;
import java.util.function.Supplier;

import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

final class HomeRealmContext {

    private HomeRealmContext() {
    }

    static void run(KeycloakSession session, RealmModel homeRealm, Runnable block) {
        Objects.requireNonNull(block, "block");
        call(session, homeRealm, () -> {
            block.run();
            return null;
        });
    }

    static <T> T call(KeycloakSession session, RealmModel homeRealm, Supplier<T> block) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(homeRealm, "homeRealm");
        Objects.requireNonNull(block, "block");
        KeycloakContext context = session.getContext();
        RealmModel previous = context.getRealm();
        try {
            context.setRealm(homeRealm);
            return block.get();
        } finally {
            context.setRealm(previous);
        }
    }

    static <T> T callIncludingAfterCompletion(KeycloakSession session, RealmModel homeRealm, Supplier<T> block) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(homeRealm, "homeRealm");
        Objects.requireNonNull(block, "block");
        KeycloakContext context = session.getContext();
        RealmModel previous = context.getRealm();
        session.getTransactionManager().enlistAfterCompletion(new SetRealm(context, homeRealm));
        try {
            return call(session, homeRealm, block);
        } finally {
            session.getTransactionManager().enlistAfterCompletion(new SetRealm(context, previous));
        }
    }

    private static final class SetRealm extends AbstractKeycloakTransaction {

        private final KeycloakContext context;
        private final RealmModel realm;

        SetRealm(KeycloakContext context, RealmModel realm) {
            this.context = context;
            this.realm = realm;
        }

        @Override
        protected void commitImpl() {
            context.setRealm(realm);
        }

        @Override
        protected void rollbackImpl() {
        }
    }
}
