package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapter;

/** A read-only copy of a home realm user, as the entry realm shows it. The copy is not stored. */
final class CrossRealmUser extends AbstractUserAdapter {

    private final String username;
    private final String email;
    private final boolean emailVerified;
    private final String firstName;
    private final String lastName;
    private final boolean enabled;

    /** Reads the home user at once, so the caller must run it with the home realm as the context realm. */
    CrossRealmUser(KeycloakSession session, RealmModel entryRealm, ComponentModel component, HomeUserId id,
            UserModel homeUser) {
        super(session, entryRealm, component);
        storageId = new StorageId(component.getId(), id.externalId());
        username = homeUser.getUsername();
        email = homeUser.getEmail();
        emailVerified = homeUser.isEmailVerified();
        firstName = homeUser.getFirstName();
        lastName = homeUser.getLastName();
        enabled = homeUser.isEnabled();
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public boolean isEmailVerified() {
        return emailVerified;
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    // The mappers of the email and profile scopes read attributes, not the getters.
    @Override
    public String getFirstAttribute(String name) {
        return switch (name) {
            case UserModel.USERNAME -> getUsername();
            case UserModel.EMAIL -> getEmail();
            case UserModel.FIRST_NAME -> getFirstName();
            case UserModel.LAST_NAME -> getLastName();
            default -> null;
        };
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        return Stream.ofNullable(getFirstAttribute(name));
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        MultivaluedHashMap<String, String> attributes = new MultivaluedHashMap<>();
        for (String name : List.of(UserModel.USERNAME, UserModel.EMAIL, UserModel.FIRST_NAME, UserModel.LAST_NAME)) {
            String value = getFirstAttribute(name);
            if (value != null) {
                attributes.add(name, value);
            }
        }
        return attributes;
    }

    @Override
    public SubjectCredentialManager credentialManager() {
        return ReadOnlyCredentialManager.INSTANCE;
    }
}
