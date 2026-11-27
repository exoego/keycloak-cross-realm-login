# Concepts

This document defines the terms used in this project.

This project is in early development. The terms describe the plan, and they can change.

## Realms

| Term | Meaning |
|---|---|
| Entry realm | The realm that every user logs in to first. It stores no user and no credential. |
| Home realm | A realm that stores users and their credentials. There is usually one for each tenant. |
| Context realm | The realm of the current request in Keycloak. Some parts of Keycloak, such as the organization rules of a user, read it and not the realm of the user. During the first step, the context realm is the entry realm. So the extension switches it to the home realm for work on a user of that realm, and puts it back. |

## Users

| Term | Meaning |
|---|---|
| Identifier | What the user types to say who they are. It is a username or an email address. |
| Candidate | A user of a home realm that the identifier matches. One identifier can have several candidates. |
| Match | A candidate whose credential check succeeded. One identifier and one password can have several matches, when one person has accounts in several home realms with the same password. The user then chooses a realm. |
| Proxy user | The user that the entry realm shows during a login. It stands for one candidate and is not stored. |

## Login

| Term | Meaning |
|---|---|
| First step | The login of the user at the entry realm, with a password or a passkey. |
| Second step | The login of the client at the home realm. It uses the entry realm as an identity provider and shows no page. |
| Entry session | The user session of the entry realm that the first step creates. The second step removes it. |
| Direct login | A login at a home realm that does not go through the entry realm. It is the reference. A login through the entry realm must obey the same rules of the home realm. |

The flow uses the terms in this order.

1. The user types an identifier and a credential at the entry realm. This is the first step.
2. The extension finds the candidates in the home realms and checks the credential against each of them.
3. If there are several matches, the user chooses a realm.
4. The entry realm shows a proxy user for the chosen candidate and creates an entry session. The token names the home realm.
5. The client starts the second step at the home realm.
6. The home realm removes the entry session.
