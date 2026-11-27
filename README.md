# keycloak-cross-realm-login

A Keycloak extension for a deployment with one realm for each tenant.

A user logs in on one shared page with a password or a passkey. The extension checks the credential against the realms that hold users, and only then tells the client which realm the user belongs to.

No endpoint answers which realm a username belongs to before the login succeeds.

## How it works

The *entry realm* is the realm that every user logs in to first. It stores no user and no credential.
A *home realm* stores users and their credentials. There is usually one for each tenant.

This is the plan. None of it works today.

1. The user logs in at the entry realm with a password or a passkey.
2. The extension checks the credential against the home realms.
3. The token of the entry realm names the home realm.
4. The client starts a login at the home realm. The home realm uses the entry realm as an identity provider and shows no page.
5. The home realm removes the session of the entry realm.

See [Concepts](docs/concepts.md) for the terms.

## Status

This project is in early development. There is no release.

The lookup of candidates exists. It finds the users of the home realms that an identifier matches.
No login works yet, because no authenticator uses the lookup.

The [open issues](https://github.com/exoego/keycloak-cross-realm-login/issues) show what is planned.

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

The extension uses internal APIs of Keycloak. A new minor version of Keycloak can break it.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md) for how to report a vulnerability.

## License

[Apache License 2.0](LICENSE)
