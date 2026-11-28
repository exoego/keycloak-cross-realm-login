# keycloak-cross-realm-login

A Keycloak extension for a deployment with one realm for each tenant.

A user logs in on one shared page with a password or a passkey. The extension checks the credential against the realms that hold users, and only then tells the client which realm the user belongs to.

No endpoint answers which realm a username belongs to before the login succeeds.

## How it works

The *entry realm* is the realm that every user logs in to first. It stores no user and no credential.
A *home realm* stores users and their credentials. There is usually one for each tenant.

Steps 1 and 2 work today. The rest is the plan.

1. The user logs in at the entry realm with a password or a passkey.
2. The extension checks the credential against the home realms.
3. The token of the entry realm names the home realm.
4. The client starts a login at the home realm. The home realm uses the entry realm as an identity provider and shows no page.
5. The home realm removes the session of the entry realm.

See [Concepts](docs/concepts.md) for the terms, and [Decisions](docs/decisions.md) for the design choices.

## Status

This project is in early development. There is no release.

A user can log in at the entry realm with a password.
A user can also log in at the entry realm with a passkey of the home realm.
When the password matches users in several home realms, the page asks which realm to use.
The brute force protection of the home realm applies to these logins.
A login from the entry realm rehashes the password by the policy of the home realm.
A failed login writes an event in the home realm.
The client cannot yet go on to the home realm.

The [open issues](https://github.com/exoego/keycloak-cross-realm-login/issues) show what is planned.

## Home realm in the token

The proxy user has two read-only attributes.

| Attribute | Value | Suggested claim |
|---|---|---|
| `home_realm` | The name of the home realm. | `home_realm` |
| `home_realm_display_name` | The display name of the home realm. The attribute is missing when the home realm has no display name. | `home_realm_display_name` |

Add a standard "User Attribute" mapper to the client of the entry realm for each attribute.
Set "User Attribute" to the attribute name, "Token Claim Name" to the claim name, and "Claim JSON Type" to `String`.
Turn on "Add to ID token".

The entry realm needs no user profile setting.
The mapper reads the attributes even when the user profile does not declare them.

The token of the entry realm carries no roles and no groups of the home realm.
It only proves who logged in and names the home realm.

## Passkeys

The entry realm and the home realms must share the relying party ID.
The default is the host name, so all realms must be served from one host name.

The passwordless policy of the entry realm turns the passkey button on.
The home realm decides the checks.
It uses its own relying party ID, its own extra origins, and its own lockout.
It checks user verification when its own policy or the policy of the entry realm requires it.

A failed passkey does not count as a failed login for the brute force protection.
It writes an event in the home realm.

Keycloak names a passkey by the username.
A person with the same username in two home realms sees two entries that look the same in the browser.
A home realm can change that name with its own login theme, in the template `webauthn-register.ftl`.

## Configuration

The extension has its own SPI, `cross-realm-login`, with one provider, `default`.
It has one setting.

| Setting | Meaning |
|---|---|
| `excluded-realm-ids` | IDs of realms that are never searched, separated by commas. Blank entries are ignored. |

The lookup searches every enabled realm.
It skips the `master` realm, the entry realm, and the realms of this setting.

```sh
--spi-cross-realm-login--default--excluded-realm-ids=5f0c3a52-8b1e-4d2a-9c47-2e6d1b0a7f31,9a1d7c3e-4b62-4f0a-8e15-6c2b9d0f3a74
KC_SPI_CROSS_REALM_LOGIN__DEFAULT__EXCLUDED_REALM_IDS=5f0c3a52-8b1e-4d2a-9c47-2e6d1b0a7f31,9a1d7c3e-4b62-4f0a-8e15-6c2b9d0f3a74
```

The setting takes IDs and not names, because the ID stays when a realm is renamed.

The lookup reads the Keycloak database. A user of a user federation is found only when Keycloak imported it, and this is not tested.

## Supported versions

| Item | Version |
|---|---|
| Keycloak | 26.8 |
| Java | 21 |
| Database | PostgreSQL |

The extension uses internal APIs of Keycloak. A new minor version of Keycloak can break it.

The tests run only with PostgreSQL. The other databases of Keycloak may work, and nothing tests them.

## Testing

The test of the passkey chooser of Chrome is off by default. Run it with `./gradlew integrationTest -Pchooser`. On macOS the terminal app needs the accessibility permission to control Chrome through System Events, and on Linux it needs a display, a D-Bus session, and AT-SPI with its Python bindings (`xvfb dbus-x11 at-spi2-core python3-gi gir1.2-atspi-2.0`). Run Gradle under `xvfb-run dbus-run-session --`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md) for how to report a vulnerability.

## License

[Apache License 2.0](LICENSE)
