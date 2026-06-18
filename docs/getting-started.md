# Getting started

This page shows how to add jEAP DB Schema Publisher to a Spring Boot service so that its database
schema is uploaded to the jEAP Architecture Repository Service (archrepo) on startup. For the bigger
picture see [How it works](how-it-works.md); for all properties see the
[Configuration reference](configuration.md).

## 1. Add the dependency

Add the starter to the Maven module containing your Spring Boot application. The version is managed by
the jEAP Spring Boot parent.

```xml
<dependency>
    <groupId>ch.admin.bit.jeap</groupId>
    <artifactId>jeap-db-schema-publisher-starter</artifactId>
</dependency>
```

## 2. Configure the archrepo URL

The upload is enabled by pointing the publisher at an archrepo instance. Setting `jeap.archrepo.url`
activates the schema upload, provided the application has a `DataSource` configured.

```yaml
jeap:
  archrepo:
    url: https://internal-csp.applicationplatform-dev.mycompany.ch/applicationplatform-archrepo-service
```

If no URL is configured, the archrepo client bean is not created and nothing is published. To disable
the auto-configuration entirely (for example in tests) set `jeap.archrepo.enabled=false`.

## 3. Configure OAuth2

The archrepo API is protected by OAuth2 client credentials. The publisher expects a client
registration named `archrepo-client` under `spring.security.oauth2.client.registration`. See
[Authentication](authentication.md) for the full setup and the required role.

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

## 4. That is all

There is no code to write. On the next startup the publisher reads the schema from the application's
`DataSource` (default schema name `data`) and posts it to the archrepo. Watch for these log lines:

```text
Reading database model from schema: data
Publishing schema DTO: componentName=..., tableCount=... to <url> with client registration archrepo-client
Published database schema successfully
```

Because the upload is best-effort, a failure is logged as `Failed to publish database schema` but does
not affect the application.

## Related

- [How it works](how-it-works.md)
- [Configuration reference](configuration.md)
- [Authentication](authentication.md)
- [jeap-db-schema-publisher](../README.md)
