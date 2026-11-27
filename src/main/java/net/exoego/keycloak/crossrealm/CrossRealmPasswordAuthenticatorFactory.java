package net.exoego.keycloak.crossrealm;

import java.util.List;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.provider.ProviderConfigProperty;

/** The username and password form for the entry realm. */
public class CrossRealmPasswordAuthenticatorFactory implements AuthenticatorFactory {

    public static final String ID = "cross-realm-password";

    private static final Authenticator AUTHENTICATOR = new CrossRealmPasswordAuthenticator();

    /** Keycloak creates the factory through {@link java.util.ServiceLoader}. */
    public CrossRealmPasswordAuthenticatorFactory() {
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return AUTHENTICATOR;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "Cross Realm Password Form";
    }

    @Override
    public String getReferenceCategory() {
        return PasswordCredentialModel.TYPE;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Checks the username and the password against the users of the home realms.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }
}
