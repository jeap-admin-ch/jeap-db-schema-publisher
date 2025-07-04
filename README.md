# jEAP DB Schema Publisher

jEAP DB Schema Publisher is a library based on Spring Boot to publish the DB schema of a jEAP application to the
jEAP Architecture Repository Service.

* If activated by providing a valid `jeap.archrepo.url` property, it will
   automatically publish the DB schema to the jEAP Architecture Repository Service.
* The request to the Archrepo service will be authenticated using OAuth2. By default, an oauth client registration named
  `archrepo-client` is expected to be configured under `spring.security.oauth2.client.registration`.
* The DB schema will be read and published at application startup, on a best-effort basis. The DB schema upload
   process is designed to have the least possible impact on application, i.e. it will not block the application or
   cause startup to fail if the upload fails.

See [ArchRepoProperties](./src/main/java/ch/admin/jeap/archrepo/ArchRepoProperties.java) for a reference of all the
available configuration properties.

## Changes

This library is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Note

This repository is part the open source distribution of jEAP. See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
