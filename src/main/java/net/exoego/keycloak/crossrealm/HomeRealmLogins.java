package net.exoego.keycloak.crossrealm;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.ws.rs.core.UriInfo;

import org.keycloak.authentication.authenticators.util.AuthenticatorUtils;
import org.keycloak.common.ClientConnection;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.managers.BruteForceProtector;

final class HomeRealmLogins {

    static final String ENTRY_REALM = "entry_realm";

    private final KeycloakSession session;
    private final RealmModel entryRealm;
    private final ClientConnection connection;
    private final BruteForceProtector protector;

    HomeRealmLogins(KeycloakSession session, RealmModel entryRealm, ClientConnection connection) {
        this.session = session;
        this.entryRealm = entryRealm;
        this.connection = connection;
        this.protector = session.getProvider(BruteForceProtector.class);
    }

    HomeAttempt check(Candidate candidate, String password) {
        return HomeRealmContext.call(session, candidate.realm(), () -> {
            UserModel user = candidate.user();
            // Every check runs, so a disabled or locked-out user costs the same time.
            boolean valid = user.credentialManager().isValid(UserCredentialModel.password(password));
            String lockout = lockoutError(candidate);
            return new HomeAttempt(candidate, valid, user.isEnabled(), lockout);
        });
    }

    HomeAttempt recheck(Candidate candidate) {
        return HomeRealmContext.call(session, candidate.realm(),
                () -> new HomeAttempt(candidate, true, candidate.user().isEnabled(), lockoutError(candidate)));
    }

    void recordFailures(List<HomeAttempt> attempts, String identifier) {
        for (HomeAttempt attempt : attempts) {
            RealmModel home = attempt.candidate().realm();
            HomeRealmContext.run(session, home, () -> {
                new EventBuilder(home, session, connection)
                        .event(EventType.LOGIN)
                        .user(attempt.candidate().user())
                        .detail(Details.USERNAME, identifier == null ? null : identifier.trim())
                        .detail(ENTRY_REALM, entryRealm.getName())
                        .error(errorOf(attempt));
                if (attempt.countsAsFailure() && home.isBruteForceProtected()) {
                    protector.failedLogin(home, attempt.candidate().user(), connection, uri(), failureCategories());
                }
            });
        }
    }

    void recordSuccess(Candidate candidate) {
        RealmModel home = candidate.realm();
        if (home.isBruteForceProtected()) {
            HomeRealmContext.run(session, home, () -> protector.successfulLogin(home, candidate.user(), connection,
                    uri(), Set.of(PasswordCredentialModel.TYPE)));
        }
    }

    private String lockoutError(Candidate candidate) {
        return AuthenticatorUtils.getDisabledByBruteForceEventError(protector, session, candidate.realm(),
                candidate.user());
    }

    private static String errorOf(HomeAttempt attempt) {
        if (attempt.lockoutError() != null) {
            return attempt.lockoutError();
        }
        return attempt.passwordValid() ? Errors.USER_DISABLED : Errors.INVALID_USER_CREDENTIALS;
    }

    private Set<String> failureCategories() {
        return Optional.ofNullable(AuthenticationManager.getAuthenticationCategory(session,
                        CrossRealmPasswordAuthenticatorFactory.ID))
                .map(Collections::singleton)
                .orElse(null);
    }

    private UriInfo uri() {
        return session.getContext().getHttpRequest().getUri();
    }
}
