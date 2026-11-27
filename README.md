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

## Status

This project is in early development. Nothing works yet, and there is no release.

The [open issues](https://github.com/exoego/keycloak-cross-realm-login/issues) show what is planned.

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
