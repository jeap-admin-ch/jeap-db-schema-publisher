# Configuration reference

All properties use the prefix `jeap.archrepo`. They are bound by `ArchRepoProperties`.

| Name                                 | Default           | Description                                                                                                                               |
|--------------------------------------|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `jeap.archrepo.url`                  | —                 | URL of the archrepo to publish the schema to. If unset, no archrepo client is created and nothing is published                            |
| `jeap.archrepo.enabled`              | `true`            | If `false`, the whole auto-configuration is switched off (useful in tests). When `true`, publishing happens as soon as a `url` is set     |
| `jeap.archrepo.oauth-client`         | `archrepo-client` | Id of the OAuth2 client registration used to authenticate with the archrepo (under `spring.security.oauth2.client.registration`)          |
| `jeap.archrepo.database.schema-name` | `data`            | Name of the database schema to read and publish                                                                                          |

## When does the upload happen?

The publisher only uploads when all of the following hold:

- `jeap.archrepo.enabled` is `true` (the default), so the auto-configuration is active.
- `jeap.archrepo.url` is set, so the archrepo HTTP client bean is created.
- The application provides a `DataSource` bean.

If `jeap.archrepo.url` is set but no OAuth2 client registration with the configured `oauth-client` id
exists, startup fails fast with an `IllegalStateException` explaining the missing registration.

## Example

```yaml
jeap:
  archrepo:
    url: https://internal-csp.applicationplatform-{env}.mycompany.ch/applicationplatform-archrepo-service
    # The following properties are shown with their defaults and usually do not need to be set
    oauth-client: archrepo-client
    enabled: true
    database:
      schema-name: data
```

When configuring many microservices, place the shared parts of this configuration in a common location
(for example the `conf/aws-jeap-base/app-config-common.yml` file when using AWS AppConfig) to avoid
duplication. Match the archrepo and authorization-server URLs to your deployment environment.

## Related

- [Getting started](getting-started.md)
- [Authentication](authentication.md)
- [How it works](how-it-works.md)
- [jeap-db-schema-publisher](../README.md)
