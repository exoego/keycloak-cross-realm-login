package net.exoego.keycloak.crossrealm;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.storage.ReadOnlyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadOnlyCredentialManagerTest {

    private final SubjectCredentialManager manager = ReadOnlyCredentialManager.INSTANCE;

    @Test
    void readsNothing() {
        assertEquals(List.of(), manager.getStoredCredentialsStream().toList());
        assertEquals(List.of(), manager.getStoredCredentialsByTypeStream("password").toList());
        assertEquals(List.of(), manager.getDisableableCredentialTypesStream().toList());
        assertNull(manager.getStoredCredentialById("id"));
        assertNull(manager.getStoredCredentialByNameAndType("name", "password"));
        assertFalse(manager.isConfiguredFor("password"));
        assertFalse(manager.isValid(List.of(UserCredentialModel.password("secret"))));
    }

    @Test
    void everyWriteFails() {
        CredentialModel model = new CredentialModel();
        assertThrows(ReadOnlyException.class, () -> manager.updateCredential(UserCredentialModel.password("secret")));
        assertThrows(ReadOnlyException.class, () -> manager.updateStoredCredential(model));
        assertThrows(ReadOnlyException.class, () -> manager.createStoredCredential(model));
        assertThrows(ReadOnlyException.class, () -> manager.removeStoredCredentialById("id"));
        assertThrows(ReadOnlyException.class, () -> manager.moveStoredCredentialTo("id", null));
        assertThrows(ReadOnlyException.class, () -> manager.updateCredentialLabel("id", "label"));
        assertThrows(ReadOnlyException.class, () -> manager.disableCredentialType("password"));
    }
}
