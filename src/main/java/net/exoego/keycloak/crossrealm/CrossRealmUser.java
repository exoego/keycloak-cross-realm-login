package net.exoego.keycloak.crossrealm;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapter;

final class CrossRealmUser extends AbstractUserAdapter {

    private final Map<String, String> profile;
    private final boolean emailVerified;
    private final boolean enabled;

    CrossRealmUser(KeycloakSession session, RealmModel entryRealm, ComponentModel component, HomeUserId id,
            UserModel homeUser) {
        super(session, entryRealm, component);
        storageId = new StorageId(component.getId(), id.externalId());
        Map<String, String> values = new HashMap<>();
        values.put(UserModel.USERNAME, homeUser.getUsername());
        values.put(UserModel.EMAIL, homeUser.getEmail());
        values.put(UserModel.FIRST_NAME, homeUser.getFirstName());
        values.put(UserModel.LAST_NAME, homeUser.getLastName());
        values.values().removeIf(Objects::isNull);
        profile = Collections.unmodifiableMap(values);
        emailVerified = homeUser.isEmailVerified();
        enabled = homeUser.isEnabled();
    }

    @Override
    public String getUsername() {
        return getFirstAttribute(UserModel.USERNAME);
    }

    @Override
    public String getEmail() {
        return getFirstAttribute(UserModel.EMAIL);
    }

    @Override
    public String getFirstName() {
        return getFirstAttribute(UserModel.FIRST_NAME);
    }

    @Override
    public String getLastName() {
        return getFirstAttribute(UserModel.LAST_NAME);
    }

    @Override
    public boolean isEmailVerified() {
        return emailVerified;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getFirstAttribute(String name) {
        return profile.get(name);
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        return Stream.ofNullable(profile.get(name));
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        MultivaluedHashMap<String, String> attributes = new MultivaluedHashMap<>();
        profile.forEach(attributes::add);
        return attributes;
    }

    @Override
    public void addRequiredAction(String action) {
    }

    @Override
    public void removeRequiredAction(String action) {
    }

    @Override
    public void addRequiredAction(RequiredAction action) {
    }

    @Override
    public void removeRequiredAction(RequiredAction action) {
    }

    @Override
    public SubjectCredentialManager credentialManager() {
        return ReadOnlyCredentialManager.INSTANCE;
    }
}
