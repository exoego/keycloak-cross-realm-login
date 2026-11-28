package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.stream.Stream;

import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.storage.ReadOnlyException;

@SuppressWarnings("deprecation")
final class ReadOnlyCredentialManager implements SubjectCredentialManager {

    static final ReadOnlyCredentialManager INSTANCE = new ReadOnlyCredentialManager();

    private ReadOnlyCredentialManager() {
    }

    @Override
    public boolean isValid(List<CredentialInput> inputs) {
        return false;
    }

    @Override
    public boolean updateCredential(CredentialInput input) {
        throw readOnly();
    }

    @Override
    public void updateStoredCredential(CredentialModel cred) {
        throw readOnly();
    }

    @Override
    public CredentialModel createStoredCredential(CredentialModel cred) {
        throw readOnly();
    }

    @Override
    public boolean removeStoredCredentialById(String id) {
        throw readOnly();
    }

    @Override
    public CredentialModel getStoredCredentialById(String id) {
        return null;
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsStream() {
        return Stream.empty();
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsByTypeStream(String type) {
        return Stream.empty();
    }

    @Override
    public CredentialModel getStoredCredentialByNameAndType(String name, String type) {
        return null;
    }

    @Override
    public boolean moveStoredCredentialTo(String id, String newPreviousCredentialId) {
        throw readOnly();
    }

    @Override
    public void updateCredentialLabel(String credentialId, String credentialLabel) {
        throw readOnly();
    }

    @Override
    public void disableCredentialType(String credentialType) {
        throw readOnly();
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream() {
        return Stream.empty();
    }

    @Override
    public boolean isConfiguredFor(String type) {
        return false;
    }

    @Override
    public boolean isConfiguredLocally(String type) {
        return false;
    }

    @Override
    public Stream<String> getConfiguredUserStorageCredentialTypesStream() {
        return Stream.empty();
    }

    @Override
    public CredentialModel createCredentialThroughProvider(CredentialModel model) {
        throw readOnly();
    }

    private static ReadOnlyException readOnly() {
        return new ReadOnlyException("The user has no credentials in this realm");
    }
}
