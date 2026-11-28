package net.exoego.keycloak.crossrealm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.authentication.authenticators.util.AuthenticatorUtils;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;

final class CrossRealmPasswordAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(CrossRealmPasswordAuthenticator.class);
    private static final String CHOICE_FIELD = "cross-realm-choice";
    private static final String CHOICE_TEMPLATE = "cross-realm-choose-realm.ftl";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        context.challenge(context.form().createLoginUsernamePassword());
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
        if (form.containsKey(CHOICE_FIELD)) {
            choose(context, form.getFirst(CHOICE_FIELD));
            return;
        }
        forgetMatches(context);
        String identifier = form.getFirst(AuthenticationManager.FORM_USERNAME);
        HomeRealmLogins logins = logins(context);
        List<HomeAttempt> attempts = attempts(context, logins, identifier,
                form.getFirst(CredentialRepresentation.PASSWORD));
        List<Candidate> matches = attempts.stream().filter(HomeAttempt::matches).map(HomeAttempt::candidate).toList();
        if (matches.isEmpty()) {
            logins.recordFailures(attempts, identifier);
            invalidCredentials(context);
        } else if (matches.size() == 1) {
            logins.recordSuccess(matches.get(0));
            context.setUser(proxyUser(context, matches.get(0)));
            context.success(PasswordCredentialModel.TYPE);
        } else {
            offerChoice(context, identifier, matches);
        }
    }

    private static void offerChoice(AuthenticationFlowContext context, String identifier, List<Candidate> matches) {
        List<Candidate> sorted = matches.stream()
                .sorted(Comparator.comparing((Candidate match) -> label(match.realm()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(match -> match.realm().getName()))
                .toList();
        Map<String, Long> labelCounts = sorted.stream()
                .collect(Collectors.groupingBy(match -> label(match.realm()), Collectors.counting()));
        List<Map<String, String>> choices = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            String label = label(sorted.get(i).realm());
            String realmName = labelCounts.get(label) > 1 ? sorted.get(i).realm().getName() : "";
            choices.add(Map.of("index", String.valueOf(i), "label", label, "realmName", realmName));
        }
        AuthenticationSessionModel session = context.getAuthenticationSession();
        // Never store the password here.
        session.setAuthNote(PendingMatches.NOTE, new PendingMatches(sorted.stream()
                .map(match -> new HomeUserId(match.realm().getId(), match.user().getId()))
                .toList()).encode());
        session.setAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME, identifier);
        session.setAuthNote(AbstractUsernameFormAuthenticator.USERNAME_HIDDEN, "true");
        context.challenge(context.form()
                .setAttribute("choices", choices)
                .setAttribute("choiceField", CHOICE_FIELD)
                .createForm(CHOICE_TEMPLATE));
    }

    private static String label(RealmModel realm) {
        String displayName = realm.getDisplayName();
        return displayName == null || displayName.isBlank() ? realm.getName() : displayName;
    }

    private static void choose(AuthenticationFlowContext context, String choice) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        List<HomeUserId> matches = PendingMatches.decode(session.getAuthNote(PendingMatches.NOTE)).matches();
        String identifier = session.getAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME);
        forgetMatches(context);
        int index = parseIndex(choice);
        if (index < 0 || index >= matches.size()) {
            invalidCredentials(context);
            return;
        }
        Optional<Candidate> candidate = context.getSession().getProvider(CandidateLookup.class)
                .find(context.getRealm(), matches.get(index).realmId(), matches.get(index).userId());
        if (candidate.isEmpty()) {
            invalidCredentials(context);
            return;
        }
        HomeRealmLogins logins = logins(context);
        HomeAttempt attempt = logins.recheck(candidate.get());
        if (!attempt.matches()) {
            logins.recordFailures(List.of(attempt), identifier);
            invalidCredentials(context);
            return;
        }
        UserModel proxy = findProxy(context, matches.get(index));
        if (proxy == null || !proxy.isEnabled()) {
            invalidCredentials(context);
            return;
        }
        logins.recordSuccess(candidate.get());
        context.setUser(proxy);
        context.success(PasswordCredentialModel.TYPE);
    }

    private static int parseIndex(String choice) {
        try {
            return Integer.parseInt(choice);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void forgetMatches(AuthenticationFlowContext context) {
        AuthenticationSessionModel session = context.getAuthenticationSession();
        session.removeAuthNote(PendingMatches.NOTE);
        session.removeAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME);
        session.removeAuthNote(AbstractUsernameFormAuthenticator.USERNAME_HIDDEN);
    }

    private static void invalidCredentials(AuthenticationFlowContext context) {
        Response challenge = context.form()
                .addError(new FormMessage(Validation.FIELD_USERNAME, Messages.INVALID_USER))
                .createLoginUsernamePassword();
        context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
    }

    private static HomeRealmLogins logins(AuthenticationFlowContext context) {
        return new HomeRealmLogins(context.getSession(), context.getRealm(), context.getConnection());
    }

    private static List<HomeAttempt> attempts(AuthenticationFlowContext context, HomeRealmLogins logins,
            String identifier, String password) {
        boolean hasPassword = password != null && !password.isEmpty();
        List<Candidate> candidates = hasPassword
                ? context.getSession().getProvider(CandidateLookup.class).findByIdentifier(context.getRealm(), identifier)
                : List.of();
        if (candidates.isEmpty()) {
            // The answer must not come sooner when nobody has the identifier.
            AuthenticatorUtils.dummyHash(context);
        }
        // Keycloak locks each checked user until the request ends. Checking in one order avoids a deadlock.
        return candidates.stream()
                .sorted(Comparator.comparing((Candidate candidate) -> candidate.user().getId()))
                .map(candidate -> logins.check(candidate, password))
                .toList();
    }

    private static UserModel proxyUser(AuthenticationFlowContext context, Candidate candidate) {
        HomeUserId home = new HomeUserId(candidate.realm().getId(), candidate.user().getId());
        UserModel proxy = findProxy(context, home);
        if (proxy == null) {
            throw new IllegalStateException("The user storage did not find the user " + home.externalId());
        }
        return proxy;
    }

    private static UserModel findProxy(AuthenticationFlowContext context, HomeUserId home) {
        RealmModel realm = context.getRealm();
        ComponentModel component = realm.getStorageProviders(UserStorageProvider.class)
                .filter(model -> CrossRealmUserStorageProviderFactory.ID.equals(model.getProviderId()))
                .findFirst()
                .orElseThrow(() -> misconfigured(realm));
        return context.getSession().users().getUserById(realm, StorageId.keycloakId(component, home.externalId()));
    }

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
