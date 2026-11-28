package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.EventRepresentation;
import org.keycloak.representations.idm.OrganizationDomainRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class TempRealmBase implements AutoCloseable {

    private final String name;
    private final RealmResource realm;

    TempRealmBase(String prefix, RealmRepresentation rep) {
        name = prefix + UUID.randomUUID();
        rep.setRealm(name);
        SharedRealmKeys.addTo(rep);
        ADMIN.realms().create(rep);
        realm = ADMIN.realm(name);
    }

    final String name() {
        return name;
    }

    final String id() {
        return realm.toRepresentation().getId();
    }

    final RealmResource resource() {
        return realm;
    }

    final void setDisplayName(String displayName) {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setDisplayName(displayName);
        realm.update(rep);
    }

    final void disable() {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setEnabled(false);
        realm.update(rep);
    }

    final void protectFromBruteForce(int failureFactor) {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setBruteForceProtected(true);
        rep.setFailureFactor(failureFactor);
        rep.setQuickLoginCheckMilliSeconds(0L);
        realm.update(rep);
    }

    final void lockOutPermanently() {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setPermanentLockout(true);
        rep.setMaxTemporaryLockouts(0);
        realm.update(rep);
    }

    final void setBruteForceProtected(boolean protectedFromBruteForce) {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setBruteForceProtected(protectedFromBruteForce);
        realm.update(rep);
    }

    final void enableEvents() {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setEventsEnabled(true);
        realm.update(rep);
    }

    final int numFailures(String userId) {
        return (Integer) realm.attackDetection().bruteForceUserStatus(userId).get("numFailures");
    }

    final boolean isLockedOut(String userId) {
        return (Boolean) realm.attackDetection().bruteForceUserStatus(userId).get("disabled");
    }

    final List<EventRepresentation> loginErrors(String userId) {
        return realm.getEvents(List.of("LOGIN_ERROR"), null, userId, null, null, null, null, null);
    }

    final void disableUser(String userId) {
        UserRepresentation user = realm.users().get(userId).toRepresentation();
        user.setEnabled(false);
        realm.users().get(userId).update(user);
    }

    final String addUser(String username, String password, boolean enabled) {
        return addUser(username, username + "@" + name + ".example", true, password, enabled);
    }

    final String addUser(String username, String email, boolean emailVerified, String password) {
        return addUser(username, email, emailVerified, password, true);
    }

    private String addUser(String username, String email, boolean emailVerified, String password, boolean enabled) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEmailVerified(emailVerified);
        user.setEnabled(enabled);
        user.setCredentials(List.of(passwordOf(password)));
        try (Response response = realm.users().create(user)) {
            assertEquals(201, response.getStatus(), response.readEntity(String.class));
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    final String addServiceAccount(String clientId, String email) {
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(clientId);
        client.setPublicClient(false);
        client.setServiceAccountsEnabled(true);
        String id;
        try (Response response = realm.clients().create(client)) {
            assertEquals(201, response.getStatus(), response.readEntity(String.class));
            id = CreatedResponseUtil.getCreatedId(response);
        }
        UserRepresentation account = realm.clients().get(id).getServiceAccountUser();
        account.setEmail(email);
        account.setEmailVerified(true);
        realm.users().get(account.getId()).update(account);
        return account.getId();
    }

    final void allowLoginWithEmail() {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setLoginWithEmailAllowed(true);
        rep.setDuplicateEmailsAllowed(false);
        realm.update(rep);
        assertTrue(realm.toRepresentation().isLoginWithEmailAllowed());
    }

    final void forbidLoginWithEmail() {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setLoginWithEmailAllowed(false);
        realm.update(rep);
    }

    final void allowDuplicateEmails() {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setLoginWithEmailAllowed(false);
        rep.setDuplicateEmailsAllowed(true);
        realm.update(rep);
    }

    private static CredentialRepresentation passwordOf(String value) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(value);
        credential.setTemporary(false);
        return credential;
    }

    final String addOrganization(String alias, String domain) {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setOrganizationsEnabled(true);
        realm.update(rep);
        OrganizationRepresentation organization = new OrganizationRepresentation();
        organization.setName(alias);
        organization.setAlias(alias);
        organization.addDomain(new OrganizationDomainRepresentation(domain));
        try (Response response = realm.organizations().create(organization)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    @Override
    public void close() {
        disable();
    }
}
