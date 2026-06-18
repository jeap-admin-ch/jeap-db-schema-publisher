# jEAP DB Schema Publisher

jEAP DB Schema Publisher is a Spring Boot library that publishes the relational database schema of a
jEAP application to the jEAP Architecture Repository Service (archrepo). On every startup it reads the
live schema from the application's `DataSource` via JDBC metadata and uploads it, so the architecture
repository always reflects the deployed schema. It provides:

* Automatic schema upload, triggered by the Spring `ApplicationReadyEvent`
* A non-blocking, best-effort upload that never delays or fails application startup
* OAuth2 client-credentials authentication against the archrepo API
* Reading of tables, columns, primary keys and foreign keys from JDBC `DatabaseMetaData`
* Optional Micrometer tracing and timing of the publish operation

## Documentation

Start with [Getting started](docs/getting-started.md), then follow the links below.

| Topic                                                | File                                                     |
|------------------------------------------------------|----------------------------------------------------------|
| Getting started (add the dependency, enable upload)  | [docs/getting-started.md](docs/getting-started.md)       |
| How it works (startup flow, schema model)            | [docs/how-it-works.md](docs/how-it-works.md)             |
| Configuration reference (`jeap.archrepo.*`)          | [docs/configuration.md](docs/configuration.md)           |
| Authentication (OAuth2 client credentials)           | [docs/authentication.md](docs/authentication.md)         |

## Modules

Group id for all modules is `ch.admin.bit.jeap`; the version is managed by the jEAP Spring Boot parent.
Consumers depend on `jeap-db-schema-publisher-starter`.

| Module                               | Purpose                                                                                       |
|--------------------------------------|-----------------------------------------------------------------------------------------------|
| `jeap-db-schema-publisher-starter`   | Spring Boot auto-configuration; reads the schema on startup and uploads it to the archrepo     |
| `jeap-db-schema-publisher-model-reader` | Reads tables, columns, keys from JDBC `DatabaseMetaData` into a `DatabaseSchema` model        |

## Changes

This library is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Note

This repository is part the open source distribution of jEAP. See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
