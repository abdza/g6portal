## First run

A brand new instance has no administrator, so it prints a one-time setup token to the console
at startup and writes it to `setup-token.txt` in the application directory:

```
* This instance has no system administrator yet.
* Open  /setup  and enter this token to create the first administrator:
*     f5c566da657dca2d83f9b72639fc20cf432ca53714b34b9d
```

Open `/setup`, enter the token, and fill in the first administrator's user ID (this is the login
username), name, email and password. The token file is deleted as soon as the account exists, and
`/setup` stops working from then on.

This replaces the old `server.allow_setup` flow, which created a fixed `admin` / `admin1234$`
account. `/portalPage/setup` now just redirects to `/setup`.

## Grails 6.2.2 Documentation

- [User Guide](https://docs.grails.org/6.2.2/guide/index.html)
- [API Reference](https://docs.grails.org/6.2.2/api/index.html)
- [Grails Guides](https://guides.grails.org/index.html)
---

## Feature asset-pipeline-grails documentation

- [Grails Asset Pipeline Core documentation](https://www.asset-pipeline.com/manual/)

## Feature scaffolding documentation

- [Grails Scaffolding Plugin documentation](https://grails.github.io/scaffolding/latest/groovydoc/)

- [https://grails-fields-plugin.github.io/grails-fields/latest/guide/index.html](https://grails-fields-plugin.github.io/grails-fields/latest/guide/index.html)

## Feature geb documentation

- [Grails Geb Functional Testing for Grails documentation](https://github.com/grails3-plugins/geb#readme)

- [https://www.gebish.org/manual/current/](https://www.gebish.org/manual/current/)

