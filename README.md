# jEAP DB Schema Publisher

jEAP DB Schema Publisher is a library based on Spring Boot to publish the DB schema of a jEAP application to the
jEAP Architecture Repository Service.

 * If activated by providing a valid `jeap.architecture.repository.url` property, it will
   automatically publish the DB schema to the jEAP Architecture Repository Service.
 * The DB schema will be determined and published at application startup, on a best-effort basis. The DB schema upload
   process is designed to have the least possible impact on application, i.e. it will not block the application or
   cause startup to fail if the upload fails.

## Changes

This library is versioned using [Semantic Versioning](http://semver.org/) and all changes are documented in
[CHANGELOG.md](./CHANGELOG.md) following the format defined in [Keep a Changelog](http://keepachangelog.com/).

## Note

This repository is part the open source distribution of jEAP. See [github.com/jeap-admin-ch/jeap](https://github.com/jeap-admin-ch/jeap)
for more information.

## License

This repository is Open Source Software licensed under the [Apache License 2.0](./LICENSE).
