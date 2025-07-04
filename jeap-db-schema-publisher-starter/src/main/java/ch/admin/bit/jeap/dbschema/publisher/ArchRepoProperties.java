package ch.admin.bit.jeap.dbschema.publisher;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static ch.admin.bit.jeap.dbschema.publisher.ArchRepoProperties.PREFIX;

@ConfigurationProperties(prefix = PREFIX)
@Data
class ArchRepoProperties {

    static final String PREFIX = "jeap.archrepo";

    /**
     * The URL of the archrepo to which the schema will be published. If no URL is configured, the publisher will not
     * publish the schema to the archrepo at startup.
     */
    private String url;
    /**
     * The OAuth client to use for authentication with the archrepo. A client registration with this name
     * must be configured under spring.security.oauth2.client.registration.
     */
    private String oauthClient = "archrepo-client";
    /**
     * If true (default), the publisher will send the schema to the archrepo as long as an archrepo URL is set.
     */
    private boolean enabled = true;

    private DbSchemaProperties database = new DbSchemaProperties();

    public String getSchemaName() {
        return database.getSchemaName();
    }

    @Data
    public static class DbSchemaProperties {
        private String schemaName = "data";
    }
}
