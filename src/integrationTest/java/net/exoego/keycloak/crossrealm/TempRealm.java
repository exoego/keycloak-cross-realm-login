package net.exoego.keycloak.crossrealm;

import org.keycloak.representations.idm.RealmRepresentation;

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
