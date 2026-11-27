package net.exoego.keycloak.crossrealm;

import java.util.UUID;

import jakarta.ws.rs.core.Response;

import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.OrganizationDomainRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.KEYCLOAK;

/** A realm with a random name that exists for one test. */
abstract class TempRealmBase implements AutoCloseable {

    private final String name;
    private final RealmResource realm;

    TempRealmBase(String prefix, RealmRepresentation rep) {
        name = prefix + UUID.randomUUID();
        rep.setRealm(name);
        KEYCLOAK.getKeycloakAdminClient().realms().create(rep);
        realm = KEYCLOAK.getKeycloakAdminClient().realm(name);
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

    /** Turns organizations on for the realm, creates an organization and returns the ID. */
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
        realm.remove();
    }
}
