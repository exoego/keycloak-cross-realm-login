package net.exoego.keycloak.crossrealm;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.OrganizationDomainRepresentation;
import org.keycloak.representations.idm.OrganizationRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import net.exoego.keycloak.crossrealm.CandidateEndpoint.Found;

import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.EXCLUDED_REALM_ID;
import static net.exoego.keycloak.crossrealm.KeycloakEnvironment.KEYCLOAK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateLookupIT {

    // Lookups run at the entry realm. It has no user.
    private static final String ENTRY = "entry";

    @Test
    void findsAliceInTwoRealmsAndBobInOne() {
        assertEquals(List.of("home-a", "home-b"), CandidateEndpoint.realmNames(ENTRY, "alice"));
        assertEquals(List.of("home-a"), CandidateEndpoint.realmNames(ENTRY, "bob"));
    }

    @Test
    void returnsTheIdsOfTheRealmAndTheUser() {
        Found found = CandidateEndpoint.candidates(ENTRY, "bob").get(0);

        assertEquals("home-a", found.realmName());
        assertEquals("bob", found.username());
        assertEquals(realm("home-a").toRepresentation().getId(), found.realmId());
        assertEquals(userId("home-a", "bob"), found.userId());
    }

    @Test
    void findsNobodyForAnUnknownName() {
        assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "nobody"));
    }

    @Test
    void findsNobodyForABlankIdentifier() {
        assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, ""));
        assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "   "));
    }

    @Test
    void ignoresCaseAndSurroundingSpaces() {
        assertEquals(List.of("home-a", "home-b"), CandidateEndpoint.realmNames(ENTRY, "  ALICE "));
        assertEquals(List.of("home-a"), CandidateEndpoint.realmNames(ENTRY, " Bob@Home-A.Example.COM "));
    }

    @Test
    void findsAVerifiedEmailInARealmWithEmailLogin() {
        assertEquals(List.of("home-a"), CandidateEndpoint.realmNames(ENTRY, "alice@home-a.example.com"));
        assertEquals(List.of("home-b"), CandidateEndpoint.realmNames(ENTRY, "alice@home-b.example.com"));
    }

    @Test
    void doesNotFindAnUnverifiedEmail() {
        assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "dave@home-a.example.com"));
        assertEquals(List.of("home-a"), CandidateEndpoint.realmNames(ENTRY, "dave"));
    }

    @Test
    void doesNotFindAnEmailWhenTheRealmDoesNotAllowEmailLogin() {
        assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "erin@home-no-email-login.example.com"));
        assertEquals(List.of("home-no-email-login"), CandidateEndpoint.realmNames(ENTRY, "erin"));
    }

    @Test
    void doesNotFindAnEmailThatTwoUsersShare() {
        // Duplicate emails can exist when a realm is switched to login with email later.
        withRealm(realm -> {
            create(realm, user("twin-1", "twin@twins.example", true));
            create(realm, user("twin-2", "twin@twins.example", true));
            allowLoginWithEmail(realm);

            assertEquals(2, realm.users().searchByEmail("twin@twins.example", true).size());
            assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "twin@twins.example"));
            String name = realm.toRepresentation().getRealm();
            assertEquals(List.of(name), CandidateEndpoint.realmNames(ENTRY, "twin-1"));
            assertEquals(List.of(name), CandidateEndpoint.realmNames(ENTRY, "twin-2"));
        }, true);
    }

    @Test
    void anUnverifiedEmailHidesAUsernameThatLooksLikeIt() {
        withRealm(realm -> {
            // Keycloak refuses this pair while the realm allows login with email, so the switch comes last.
            create(realm, user("shared@example.com", "x@example.com", false));
            create(realm, user("y-user", "shared@example.com", false));
            allowLoginWithEmail(realm);

            assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "shared@example.com"));

            UserRepresentation y = realm.users().searchByUsername("y-user", true).get(0);
            y.setEmailVerified(true);
            realm.users().get(y.getId()).update(y);

            List<Found> found = CandidateEndpoint.candidates(ENTRY, "shared@example.com");
            assertEquals(1, found.size());
            assertEquals("y-user", found.get(0).username());
        }, false);
    }

    @Test
    void anEmailOfAServiceAccountHidesAUsernameThatLooksLikeIt() {
        withRealm(realm -> {
            ClientRepresentation client = new ClientRepresentation();
            client.setClientId("machine");
            client.setPublicClient(false);
            client.setServiceAccountsEnabled(true);
            String clientId;
            try (Response response = realm.clients().create(client)) {
                clientId = CreatedResponseUtil.getCreatedId(response);
            }
            UserRepresentation serviceAccount = realm.clients().get(clientId).getServiceAccountUser();
            serviceAccount.setEmail("ops@example.com");
            serviceAccount.setEmailVerified(true);
            realm.users().get(serviceAccount.getId()).update(serviceAccount);
            create(realm, user("ops@example.com", "other@example.com", true));
            allowLoginWithEmail(realm);

            assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "ops@example.com"));
            assertEquals(1, CandidateEndpoint.candidates(ENTRY, "other@example.com").size());
        }, false);
    }

    @Test
    void findsAUserWhoseUsernameIsItsUnverifiedEmail() {
        withRealm(realm -> {
            create(realm, user("carol@tenant.example", "carol@tenant.example", false));
            allowLoginWithEmail(realm);

            List<Found> found = CandidateEndpoint.candidates(ENTRY, "carol@tenant.example");
            assertEquals(1, found.size());
            assertEquals("carol@tenant.example", found.get(0).username());
        }, false);
    }

    @Test
    void findsAMemberOfAnOrganizationFromARealmWithOrganizations() {
        // Keycloak asks the organizations of the context realm about a user that it loads.
        withRealm(entry -> withRealm(home -> {
            createOrganization(entry, "entry-org");
            String organizationId = createOrganization(home, "home-org");
            create(home, user("member", "member@home-org.example", true));
            String memberId = home.users().searchByUsername("member", true).get(0).getId();
            home.organizations().get(organizationId).members().addMember(memberId).close();

            String entryName = entry.toRepresentation().getRealm();
            String homeName = home.toRepresentation().getRealm();
            assertEquals(List.of(homeName), CandidateEndpoint.realmNames(entryName, "member"));
        }, false), false);
    }

    @Test
    void findsADisabledUser() {
        withRealm(realm -> {
            UserRepresentation disabled = user("disabled-user", "disabled@example.com", true);
            disabled.setEnabled(false);
            create(realm, disabled);

            List<Found> found = CandidateEndpoint.candidates(ENTRY, "disabled-user");
            assertEquals(1, found.size());
            assertFalse(found.get(0).enabled());
            assertTrue(CandidateEndpoint.candidates(ENTRY, "bob").get(0).enabled());
        }, false);
    }

    @Test
    void usesTheEntryRealmOfTheCallerAndNotTheContextRealm() {
        // With home-b as the entry realm, home-b would be missing.
        List<Found> found = CandidateEndpoint.candidatesInContext("home-b", ENTRY, "alice");

        assertEquals(List.of("home-a", "home-b"), found.stream().map(Found::realmName).toList());
    }

    @Test
    void findsNobodyForAnIdentifierOver255Characters() {
        assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "a".repeat(256)));
    }

    @Test
    void neverFindsAServiceAccount() {
        assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "service-account-machine"));
    }

    @Test
    void doesNotSearchTheMasterRealm() {
        UserRepresentation alice = new UserRepresentation();
        alice.setUsername("alice");
        alice.setEnabled(true);
        withUser("master", alice, () -> {
            assertEquals(List.of("home-a", "home-b"), CandidateEndpoint.realmNames(ENTRY, "alice"));
            assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "admin"));
        });
    }

    @Test
    void doesNotSearchTheEntryRealmItself() {
        UserRepresentation visitor = new UserRepresentation();
        visitor.setUsername("entry-visitor");
        visitor.setEnabled(true);
        withUser(ENTRY, visitor, () -> {
            assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "entry-visitor"));
            // Another realm is the context realm here, so the entry realm is a home realm.
            assertEquals(List.of(ENTRY), CandidateEndpoint.realmNames("home-b", "entry-visitor"));
        });
    }

    @Test
    void doesNotSearchADisabledRealm() {
        assertEquals(List.of("home-a", "home-b"), CandidateEndpoint.realmNames(ENTRY, "alice"));
        assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "alice@disabled.example.com"));
    }

    @Test
    void doesNotSearchAnExcludedRealm() {
        assertEquals(List.of(), CandidateEndpoint.candidates(ENTRY, "alice@excluded.example.com"));
        assertFalse(CandidateEndpoint.realmNames(ENTRY, "alice").contains("home-excluded"));
        assertEquals(EXCLUDED_REALM_ID, realm("home-excluded").toRepresentation().getId());
    }

    @Test
    void findReturnsAUser() {
        String realmId = realm("home-a").toRepresentation().getId();

        Found found = CandidateEndpoint.candidate(ENTRY, realmId, userId("home-a", "bob")).orElseThrow();

        assertEquals("bob", found.username());
        assertEquals("home-a", found.realmName());
    }

    @Test
    void findReturnsNothingForTheWrongRealm() {
        String realmIdOfB = realm("home-b").toRepresentation().getId();

        assertTrue(CandidateEndpoint.candidate(ENTRY, realmIdOfB, userId("home-a", "bob")).isEmpty());
        assertTrue(CandidateEndpoint.candidate(ENTRY, "no-such-realm", userId("home-a", "bob")).isEmpty());
    }

    @Test
    void findReturnsNothingForTheWrongRealmWhenTheSessionHoldsTheUser() {
        // Keycloak 26.8.0 returns a user that the session already loaded for any realm.
        String bob = userId("home-a", "bob");
        String realmIdOfA = realm("home-a").toRepresentation().getId();
        String realmIdOfB = realm("home-b").toRepresentation().getId();

        assertTrue(CandidateEndpoint.candidateAfterLookup(ENTRY, "bob", realmIdOfA, bob).isPresent());
        assertTrue(CandidateEndpoint.candidateAfterLookup(ENTRY, "bob", realmIdOfB, bob).isEmpty());
    }

    @Test
    void findReturnsNothingForAnIdOfAUserFederation() {
        String realmId = realm("home-a").toRepresentation().getId();

        assertTrue(CandidateEndpoint.candidate(ENTRY, realmId, "f:no-such-component:bob").isEmpty());
        // Keycloak cannot parse this one.
        assertTrue(CandidateEndpoint.candidate(ENTRY, realmId, "f:bob").isEmpty());
    }

    @Test
    void findReturnsNothingForTheMasterRealm() {
        String masterId = realm("master").toRepresentation().getId();

        assertTrue(CandidateEndpoint.candidate(ENTRY, masterId, userId("master", "admin")).isEmpty());
    }

    @Test
    void findReturnsNothingForTheEntryRealm() {
        UserRepresentation visitor = new UserRepresentation();
        visitor.setUsername("entry-visitor-for-find");
        visitor.setEnabled(true);
        withUser(ENTRY, visitor, () -> {
            String id = userId(ENTRY, "entry-visitor-for-find");
            String entryId = realm(ENTRY).toRepresentation().getId();

            assertTrue(CandidateEndpoint.candidate(ENTRY, entryId, id).isEmpty());
            assertTrue(CandidateEndpoint.candidate("home-b", entryId, id).isPresent());
        });
    }

    @Test
    void findReturnsNothingForAnExcludedRealm() {
        assertTrue(CandidateEndpoint.candidate(ENTRY, EXCLUDED_REALM_ID, userId("home-excluded", "alice")).isEmpty());
    }

    @Test
    void findReturnsNothingForADisabledRealm() {
        String realmId = realm("home-disabled").toRepresentation().getId();

        assertTrue(CandidateEndpoint.candidate(ENTRY, realmId, userId("home-disabled", "alice")).isEmpty());
    }

    @Test
    void findReturnsNothingForAServiceAccount() {
        RealmResource homeA = realm("home-a");
        String clientId = homeA.clients().findByClientId("machine").get(0).getId();
        String serviceAccountId = homeA.clients().get(clientId).getServiceAccountUser().getId();

        assertTrue(CandidateEndpoint.candidate(ENTRY, homeA.toRepresentation().getId(), serviceAccountId).isEmpty());
    }

    @Test
    void findReturnsNothingForAMissingUser() {
        String realmId = realm("home-a").toRepresentation().getId();

        assertTrue(CandidateEndpoint.candidate(ENTRY, realmId, "no-such-user").isEmpty());
    }

    private static RealmResource realm(String name) {
        return KEYCLOAK.getKeycloakAdminClient().realm(name);
    }

    private static String userId(String realm, String username) {
        return realm(realm).users().searchByUsername(username, true).get(0).getId();
    }

    private static void withUser(String realm, UserRepresentation user, Runnable test) {
        String id;
        try (Response response = realm(realm).users().create(user)) {
            id = CreatedResponseUtil.getCreatedId(response);
        }
        try {
            test.run();
        } finally {
            realm(realm).users().delete(id).close();
        }
    }

    private static void allowLoginWithEmail(RealmResource realm) {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setLoginWithEmailAllowed(true);
        // Keycloak turns login with email off again when the same update allows duplicate emails.
        rep.setDuplicateEmailsAllowed(false);
        realm.update(rep);
        assertTrue(realm.toRepresentation().isLoginWithEmailAllowed());
    }

    private static String createOrganization(RealmResource realm, String name) {
        RealmRepresentation rep = realm.toRepresentation();
        rep.setOrganizationsEnabled(true);
        realm.update(rep);
        OrganizationRepresentation organization = new OrganizationRepresentation();
        organization.setName(name);
        organization.setAlias(name);
        organization.addDomain(new OrganizationDomainRepresentation(name + ".example"));
        try (Response response = realm.organizations().create(organization)) {
            return CreatedResponseUtil.getCreatedId(response);
        }
    }

    private static void create(RealmResource realm, UserRepresentation user) {
        try (Response response = realm.users().create(user)) {
            assertEquals(201, response.getStatus(), response.readEntity(String.class));
        }
    }

    private static UserRepresentation user(String username, String email, boolean verified) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEmailVerified(verified);
        user.setEnabled(true);
        return user;
    }

    // The realm has a random name, so tests do not see each other's users.
    private static void withRealm(Consumer<RealmResource> test, boolean allowDuplicateEmails) {
        String name = "tmp-" + UUID.randomUUID();
        RealmRepresentation rep = new RealmRepresentation();
        rep.setRealm(name);
        rep.setEnabled(true);
        rep.setDuplicateEmailsAllowed(allowDuplicateEmails);
        rep.setLoginWithEmailAllowed(false);
        KEYCLOAK.getKeycloakAdminClient().realms().create(rep);
        try {
            test.accept(realm(name));
        } finally {
            realm(name).remove();
        }
    }
}
