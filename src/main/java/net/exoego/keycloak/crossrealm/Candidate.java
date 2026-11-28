package net.exoego.keycloak.crossrealm;

import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public record Candidate(RealmModel realm, UserModel user) {
}
