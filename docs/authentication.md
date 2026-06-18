# Authentication

The archrepo API is protected by OAuth2. The publisher authenticates with the **client credentials**
grant: before each request it obtains an access token from the configured token endpoint and sends it
as a bearer token (`OAuth2ClientCredentialsRestClientInitializer`).

## Client registration

The publisher resolves the OAuth2 client registration whose id equals `jeap.archrepo.oauth-client`
(default `archrepo-client`) from Spring Security's `ClientRegistrationRepository`. That registration
must exist under `spring.security.oauth2.client.registration`. If it is missing, application startup
fails with an explanatory `IllegalStateException`.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          archrepo-client:
            client-id: ${your-keycloak-system-name}-archrepo-client
            client-secret: ${your-secret}
            scope: openid
            authorization-grant-type: client_credentials
            provider: archrepo-client
        provider:
          archrepo-client:
            token-uri: https://internal.keycloak.mycompany.ch/realms/bazg-applicationplatform/protocol/openid-connect/token
```

The `token-uri` differs per environment. Examples:

| Environment | `token-uri`                                                                                                       |
|-------------|------------------------------------------------------------------------------------------------------------------|
| dev         | `https://internal-csp.applicationplatform-dev.mycompany.ch/applicationplatform-oauth-mock-service/oauth2/token` |
| ref / abn   | `https://internal.keycloak-{env}.mycompany.ch/realms/bazg-applicationplatform/protocol/openid-connect/token`   |
| prod        | `https://internal.keycloak.mycompany.ch/realms/bazg-applicationplatform/protocol/openid-connect/token`        |

## Required role

The OAuth2 client must be granted the archrepo role that authorizes writing database schemas:

```text
<arch-repo-system-name>_@database-schema_#write
```

Replace `<arch-repo-system-name>` with the system name of the target architecture repository.

## Related

- [Getting started](getting-started.md)
- [Configuration reference](configuration.md)
- [How it works](how-it-works.md)
- [jeap-db-schema-publisher](../README.md)
