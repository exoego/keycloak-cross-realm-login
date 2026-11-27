# Decisions

This document records the design choices of the project and their reasons.
Each choice was discussed in an issue with the label `decision`. The issue is closed when the choice is written here.

## Which realms the entry realm searches

Decided in [#7](https://github.com/exoego/keycloak-cross-realm-login/issues/7).

The entry realm searches every enabled realm, except these.

- `master`
- The entry realm itself
- A disabled realm
- A realm whose ID is in the server setting `excluded-realm-ids`

The setting belongs to the server, not to a realm. It holds realm IDs, separated by commas.

```
--spi-cross-realm-login--default--excluded-realm-ids=<realm id>,<realm id>
```

The extension has its own SPI `cross-realm-login` for its settings. The lookup of candidates is the provider of that SPI.

Reasons.

- The purpose of the extension is a login across realms. A new tenant works with no extra step.
- The list is for a few special realms, such as a realm for internal staff or for tests.
- A realm is excluded by its ID, because the ID stays when the realm is renamed.
- Only the operator of the server can change a server setting. An administrator of the entry realm cannot remove a realm from the list.

What this gives up.

- A realm cannot keep itself out. Only the operator of the server can exclude it.
- A new realm is searched from the moment it exists. A special realm must be added to the list before it gets users.

## Which databases are supported

Decided in [#42](https://github.com/exoego/keycloak-cross-realm-login/issues/42).

PostgreSQL only. The other databases of Keycloak may work, and nothing tests them.

Reasons.

- CI rows for databases that nobody plans to use cost time and upkeep.
- The lookup compares the identifier again in Java, as Keycloak does. That keeps the result right with a collation that ignores accents, as in MySQL and MariaDB. It does no harm on PostgreSQL.

[#43](https://github.com/exoego/keycloak-cross-realm-login/issues/43) is the work to support more databases.

## How much one failed login can do across realms

Decided in [#39](https://github.com/exoego/keycloak-cross-realm-login/issues/39). Not built yet.

One request at the entry realm reaches every home realm with a candidate. A direct login reaches one realm. So one request can count failures for several users and run several password hash checks.

The extension limits the number of candidates of one request.

```
--spi-cross-realm-login--default--max-candidates=10
```

The default is 10. When an identifier has more candidates, the login fails.

- No password is checked. One dummy hash runs, as for an unknown identifier.
- The page is the same as for a wrong password.
- No failure is counted in any home realm.
- A warning in the log says how many candidates there were. It names no realm and no identifier.

The login never checks only some of the candidates. Then the order would decide which realms are checked, and a user in a realm past the limit could never log in.

The extension has no rate limit. Keycloak has none of its own, and one in the extension would need a counter that all nodes of a cluster share. The operator limits the login of the entry realm in front of Keycloak, for example in a WAF or a reverse proxy.

Identifiers must not collide across realms.

- An email address does not.
- A user without an email address gets a username of the form `<name>@<organization>`. Each home realm enforces the form with a `pattern` validator on the username in its user profile.

Reasons.

- With such identifiers, two candidates of one identifier are almost always one person with accounts in several realms. Nobody is expected to have many such accounts. So the limit costs nothing in practice.
- The limit caps the hash checks and the lockouts of one request when the data is not as expected.

What stays possible.

- One request still counts a failure for each candidate, up to the limit.
- A user with more accounts than the limit cannot use the shared page. That user logs in at a home realm directly.
- A username that does not follow the form collides with users of other realms. Above the limit, none of them can use the shared page.

A temporary lockout in the home realms is safer than a permanent one.
