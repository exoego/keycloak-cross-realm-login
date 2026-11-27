package net.exoego.keycloak.crossrealm;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.OrganizationDomainRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.KEYCLOAK;

/** An entry realm with a random name that exists for one test. It has the settings of entry-realm.json. */
final class TempEntryRealm implements AutoCloseable {

    static final String CLIENT = "entry-client";

    private final String name = "tmp-entry-" + UUID.randomUUID();
    private final RealmResource realm;

    TempEntryRealm(boolean withUserStorage) {
        RealmRepresentation rep = read();
        rep.setRealm(name);
        if (!withUserStorage) {
            rep.setComponents(null);
        }
        KEYCLOAK.getKeycloakAdminClient().realms().create(rep);
        realm = KEYCLOAK.getKeycloakAdminClient().realm(name);
    }

    String name() {
        return name;
    }

    RealmResource resource() {
        return realm;
    }

    void addOrganization(String alias, String domain) {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setOrganizationsEnabled(true);
        realm.update(rep);
        OrganizationRepresentation organization = new OrganizationRepresentation();
        organization.setName(alias);
        organization.setAlias(alias);
        organization.addDomain(new OrganizationDomainRepresentation(domain));
        try (Response response = realm.organizations().create(organization)) {
            CreatedResponseUtil.getCreatedId(response);
        }
    }

    @Override
    public void close() {
        realm.remove();
    }

    private static RealmRepresentation read() {
        try (InputStream in = TempEntryRealm.class.getResourceAsStream("/entry-realm.json")) {
            return new ObjectMapper().readValue(in, RealmRepresentation.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
