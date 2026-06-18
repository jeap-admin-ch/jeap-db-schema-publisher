# AGENTS.md

Guidance for AI coding agents working **in this repository**. For how to *use* the library in a
consuming service, read [README.md](README.md) and the [docs/](docs/) folder instead.

## Project

jEAP DB Schema Publisher is a multi-module Maven library that publishes a Spring Boot application's
relational database schema to the jEAP Architecture Repository Service (archrepo). On the Spring
`ApplicationReadyEvent` it reads the live schema from the application's `DataSource` through JDBC
`DatabaseMetaData`, maps it to a `DatabaseSchema` model, and uploads it via an OAuth2-authenticated
HTTP call. The upload runs asynchronously and best-effort, so it never blocks or fails startup.

## Repository layout

```
pom.xml                                   # Parent POM (packaging=pom); declares the modules below
jeap-db-schema-publisher-model-reader/    # Reads JDBC metadata into the DatabaseSchema model (no Spring)
jeap-db-schema-publisher-starter/         # @AutoConfiguration wiring; archrepo HTTP client; consumer-facing artifact
Jenkinsfile, publiccode.yml, CHANGELOG.md, LICENSE
```

Key types in `jeap-db-schema-publisher-starter` (package `ch.admin.bit.jeap.dbschema`):

- `publisher.DbSchemaPublisherAutoConfiguration` — `@AutoConfiguration` registering the beans below.
- `publisher.ArchRepoProperties` — `@ConfigurationProperties(prefix = "jeap.archrepo")`.
- `publisher.DbSchemaPublisherEventListener` — listens for `ApplicationReadyEvent` and triggers the publish.
- `publisher.DbSchemaPublisher` — reads the schema and posts it; `@Async` on a dedicated task executor.
- `publisher.AppVersionProvider` — resolves the app version from `BuildProperties`/`GitProperties` (falls back to `na`).
- `publisher.TracingTimer` — optional Micrometer span + timer around the publish, with graceful degradation.
- `archrepo.client.ArchitectureRepositoryService` — HTTP interface (`@PostExchange("/api/dbschemas")`).
- `archrepo.client.OAuth2ClientCredentialsRestClientInitializer` — adds the bearer token to each request.

The `model-reader` module holds `reader.DatabaseModelReader` / `DatabaseModelFactory` and the
`model.*` records (`DatabaseSchema`, `Table`, `TableColumn`, `TablePrimaryKey`, `TableForeignKey`).

## Build & test

```bash
./mvnw -pl <module> -am install      # build a module and its dependencies
./mvnw verify                        # full build incl. tests
```

- Parent: `ch.admin.bit.jeap:jeap-internal-spring-boot-parent` (Spring Boot 4 aligned).
- Integration tests (`SchemaUploadIntegrationTest`) use Testcontainers PostgreSQL (`@ServiceConnection`),
  Flyway migrations under `src/test/resources/db/migration`, and WireMock for the archrepo and OAuth endpoints.
- Spring Boot 3 maintenance happens on the `release/springboot3` branch; `master` targets Spring Boot 4.

## jEAP conventions

- Java packages live under `ch.admin.bit.jeap.dbschema...`.
- Configuration properties use the prefix `jeap.archrepo.*`.
- Auto-configuration is registered via `@AutoConfiguration` and
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- The publisher is conditional: the auto-configuration is active unless `jeap.archrepo.enabled=false`,
  the archrepo HTTP client bean requires `jeap.archrepo.url`, and the publisher bean requires both a
  `DataSource` and that client. Keep these conditions intact when changing wiring.

## Docs

When changing public behaviour, update the matching focused file under [docs/](docs/) (one topic per
file) and the documentation index in the README.

## Versioning

- Semantic Versioning; all changes documented in [CHANGELOG.md](./CHANGELOG.md) (Keep a Changelog format).
- `setPomVersions.sh` updates the version across all module POMs.
- When working on a feature branch, increase the version to `x.y.z-SNAPSHOT` in the POMs.
- Always keep the -SNAPSHOT postfix in the POMs, CI will remove it when releasing a version. Do not use the SNAPSHOT
  postfix in other places (CHANGELOG, publiccode.yml etc)
- Keep changelog entries concise and to the point, follow existing patterns
- Keep commit messages short, use the JIRA ID from the branch name as a prefix, do not use conventional commits (for
  example: "JEAP-1234 Added feature X")
- When bumping the version, also update the changelog, and update version/date in `publiccode.yml`.
- When the version on a feature branch has not yet been bumped compared to master, ask the user if a major, minor or
  patch version bump should be performed, and update the version accordingly.
