package net.exoego.keycloak.crossrealm;

import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * A user of a home realm that an identifier matches.
 *
 * @param realm the home realm
 * @param user the user, from the user cache of Keycloak
 */
public record Candidate(RealmModel realm, UserModel user) {
}
