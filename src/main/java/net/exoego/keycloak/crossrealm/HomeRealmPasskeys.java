package net.exoego.keycloak.crossrealm;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.ws.rs.core.MultivaluedMap;

import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.util.exception.WebAuthnException;
import org.keycloak.WebAuthnConstants;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.requiredactions.WebAuthnRegister;
import org.keycloak.common.util.Base64Url;
import org.keycloak.common.util.UriUtils;
import org.keycloak.credential.WebAuthnCredentialModelInput;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.WebAuthnPolicy;
import org.keycloak.models.credential.WebAuthnCredentialModel;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;

final class HomeRealmPasskeys {

    static final String FAILED = Messages.WEBAUTHN_ERROR_AUTH_VERIFICATION;

    private final KeycloakSession session;
    private final HomeRealmLogins logins;

    HomeRealmPasskeys(KeycloakSession session, HomeRealmLogins logins) {
        this.session = session;
        this.logins = logins;
    }

    record Outcome(Candidate candidate, String errorKey) {

        static Outcome success(Candidate candidate) {
            return new Outcome(candidate, null);
        }

        static Outcome failure(String errorKey) {
            return new Outcome(null, errorKey);
        }
    }

    Outcome verify(AuthenticationFlowContext context, MultivaluedMap<String, String> form) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String challenge = authSession.getAuthNote(WebAuthnConstants.AUTH_CHALLENGE_NOTE);
        authSession.removeAuthNote(WebAuthnConstants.AUTH_CHALLENGE_NOTE);
        String browserError = form.getFirst(WebAuthnConstants.ERROR);
        if (!isBlank(browserError)) {
            return Outcome.failure(WebAuthnRegister.mapBrowserApiErrorToMessageKey(browserError, false));
        }
        Optional<AuthenticationRequest> request = request(form);
        Optional<String> userId = userId(form);
        if (challenge == null || request.isEmpty() || userId.isEmpty()) {
            return Outcome.failure(FAILED);
        }
        Optional<Candidate> candidate = session.getProvider(CandidateLookup.class)
                .findByUserId(context.getRealm(), userId.get());
        if (candidate.isEmpty()) {
            return Outcome.failure(FAILED);
        }
        HomeAttempt attempt = attempt(context, candidate.get(), request.get(), challenge);
        if (attempt.matches()) {
            return Outcome.success(candidate.get());
        }
        logins.recordEvent(attempt, null);
        return Outcome.failure(FAILED);
    }

    private static Optional<String> userId(MultivaluedMap<String, String> form) {
        String userHandle = form.getFirst(WebAuthnConstants.USER_HANDLE);
        if (isBlank(userHandle)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new String(Base64Url.decode(userHandle), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private HomeAttempt attempt(AuthenticationFlowContext context, Candidate candidate, AuthenticationRequest request,
            String challenge) {
        boolean entryRequiresVerification = requiresVerification(context.getRealm().getWebAuthnPolicyPasswordless());
        String origin = UriUtils.getOrigin(context.getUriInfo().getBaseUri());
        String baseHost = context.getUriInfo().getBaseUri().getHost();
        return HomeRealmContext.call(session, candidate.realm(), () -> {
            String lockout = logins.lockoutError(candidate);
            boolean valid = false;
            if (lockout == null) {
                WebAuthnPolicy policy = candidate.realm().getWebAuthnPolicyPasswordless();
                String rpId = isBlank(policy.getRpId()) ? baseHost : policy.getRpId();
                ServerProperty server = serverProperty(origin, rpId, challenge);
                boolean verifyUser = entryRequiresVerification || requiresVerification(policy);
                valid = isValid(candidate, request, server, verifyUser);
            }
            return new HomeAttempt(candidate, valid, candidate.user().isEnabled(), lockout);
        });
    }

    @SuppressWarnings("deprecation")
    private static ServerProperty serverProperty(String origin, String rpId, String challenge) {
        return new ServerProperty(new Origin(origin), rpId, new DefaultChallenge(challenge));
    }

    private static boolean isValid(Candidate candidate, AuthenticationRequest request, ServerProperty server,
            boolean verifyUser) {
        WebAuthnCredentialModelInput input = new WebAuthnCredentialModelInput(WebAuthnCredentialModel.TYPE_PASSWORDLESS);
        input.setAuthenticationRequest(request);
        input.setAuthenticationParameters(
                new WebAuthnCredentialModelInput.KeycloakWebAuthnAuthenticationParameters(server, verifyUser));
        try {
            return candidate.user().credentialManager().isValid(input);
        } catch (WebAuthnException e) {
            return false;
        }
    }

    private static Optional<AuthenticationRequest> request(MultivaluedMap<String, String> form) {
        String credentialId = form.getFirst(WebAuthnConstants.CREDENTIAL_ID);
        String clientData = form.getFirst(WebAuthnConstants.CLIENT_DATA_JSON);
        String authenticatorData = form.getFirst(WebAuthnConstants.AUTHENTICATOR_DATA);
        String signature = form.getFirst(WebAuthnConstants.SIGNATURE);
        if (isBlank(credentialId) || isBlank(clientData) || isBlank(authenticatorData) || isBlank(signature)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AuthenticationRequest(Base64Url.decode(credentialId),
                    Base64Url.decode(authenticatorData), Base64Url.decode(clientData), Base64Url.decode(signature)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean requiresVerification(WebAuthnPolicy policy) {
        return Constants.WEBAUTHN_POLICY_OPTION_REQUIRED.equals(policy.getUserVerificationRequirement());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
