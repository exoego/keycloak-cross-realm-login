package net.exoego.keycloak.crossrealm;

import org.keycloak.representations.idm.RealmRepresentation;

/** A home realm with a random name that exists for one test. */
final class TempRealm extends TempRealmBase {

    TempRealm() {
        super("tmp-", enabledRealm());
    }

    private static RealmRepresentation enabledRealm() {
        RealmRepresentation rep = new RealmRepresentation();
        rep.setEnabled(true);
        return rep;
    }
}
