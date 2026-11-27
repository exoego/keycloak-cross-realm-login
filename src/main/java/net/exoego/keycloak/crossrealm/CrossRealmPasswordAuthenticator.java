package net.exoego.keycloak.crossrealm;

import java.util.List;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.util.AuthenticatorUtils;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;

/** Checks the password against the candidates of the identifier. */
final class CrossRealmPasswordAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(CrossRealmPasswordAuthenticator.class);

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        context.challenge(context.form().createLoginUsernamePassword());
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        String password = form.getFirst(CredentialRepresentation.PASSWORD);
        List<Candidate> matches = matches(context, form.getFirst(AuthenticationManager.FORM_USERNAME), password);
        // Several matches fail for now. A later change lets the user choose a realm.
        if (matches.size() != 1) {
            Response challenge = context.form()
                    .addError(new FormMessage(Validation.FIELD_USERNAME, Messages.INVALID_USER))
                    .createLoginUsernamePassword();
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
            return;
        }
        context.setUser(proxyUser(context, matches.get(0)));
        context.success(PasswordCredentialModel.TYPE);
    }

    private static List<Candidate> matches(AuthenticationFlowContext context, String identifier, String password) {
        KeycloakSession session = context.getSession();
        boolean hasPassword = password != null && !password.isEmpty();
        List<Candidate> candidates = hasPassword
                ? session.getProvider(CandidateLookup.class).findByIdentifier(context.getRealm(), identifier)
                : List.of();
        if (candidates.isEmpty()) {
            // The answer must not come sooner when nobody has the identifier.
            AuthenticatorUtils.dummyHash(context);
        }
        return candidates.stream().filter(candidate -> matches(session, candidate, password)).toList();
    }

    private static boolean matches(KeycloakSession session, Candidate candidate, String password) {
        // Keycloak reads rules of the user, such as organizations, from the context realm.
        return HomeRealmContext.call(session, candidate.realm(), () -> {
            UserModel user = candidate.user();
            boolean valid = user.credentialManager().isValid(UserCredentialModel.password(password));
            // The check accepts a disabled user. It runs first, so a disabled user costs the same time.
            return valid && user.isEnabled();
        });
    }

    private static UserModel proxyUser(AuthenticationFlowContext context, Candidate candidate) {
        RealmModel realm = context.getRealm();
        ComponentModel component = realm.getStorageProviders(UserStorageProvider.class)
                .filter(model -> CrossRealmUserStorageProviderFactory.ID.equals(model.getProviderId()))
                .findFirst()
                .orElseThrow(() -> misconfigured(realm));
        String id = StorageId.keycloakId(component,
                new HomeUserId(candidate.realm().getId(), candidate.user().getId()).externalId());
        UserModel proxy = context.getSession().users().getUserById(realm, id);
        if (proxy == null) {
            throw new IllegalStateException("The user storage did not find the user " + id);
        }
        return proxy;
    }

    // Keycloak logs a failed authentication as a warning only.
    private static IllegalStateException misconfigured(RealmModel realm) {
        String message = "The realm " + realm.getName() + " has no user storage of the provider "
                + CrossRealmUserStorageProviderFactory.ID;
        LOG.error(message);
        return new IllegalStateException(message);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
    }

    @Override
    public void close() {
    }
}
