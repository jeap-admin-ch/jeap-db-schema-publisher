package ch.admin.bit.jeap.dbschema.archrepo.client;

import ch.admin.bit.jeap.dbschema.model.DatabaseSchema;
import ch.admin.bit.jeap.dbschema.model.Table;
import ch.admin.bit.jeap.dbschema.model.TableColumn;
import ch.admin.bit.jeap.dbschema.model.TablePrimaryKey;
import com.github.tomakehurst.wiremock.WireMockServer;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Shared constants, schema fixtures and WireMock stubs for the architecture repository client tests.
 */
final class ArchRepoTestFixtures {

    static final String API_DBSCHEMAS_PATH = "/api/dbschemas";
    static final String OAUTH_TOKEN_PATH = "/oauth/token";
    static final String CONTENT_TYPE_HEADER = "Content-Type";
    static final String APPLICATION_JSON = "application/json";
    static final String TEST_APP = "test-app";
    static final String BIGINT = "bigint";

    private ArchRepoTestFixtures() {
    }

    /**
     * A minimal database schema with a single table.
     */
    static DatabaseSchema testDatabaseSchema() {
        TableColumn idColumn = new TableColumn("id", BIGINT, false);
        TableColumn nameColumn = new TableColumn("name", "varchar(100)", false);
        TablePrimaryKey primaryKey = new TablePrimaryKey("users_pk", List.of("id"));
        Table usersTable = new Table("users", List.of(idColumn, nameColumn), List.of(), primaryKey);
        return new DatabaseSchema("testdb", "1.0", List.of(usersTable));
    }

    /**
     * Stubs the OAuth2 token endpoint used to authenticate the archrepo client.
     */
    static void stubOAuthTokenEndpoint(WireMockServer wireMockServer) {
        wireMockServer.stubFor(post(urlEqualTo(OAUTH_TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .withBody("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    /**
     * Stubs a successful response of the archrepo db schema endpoint.
     */
    static void stubDbSchemasEndpoint(WireMockServer wireMockServer) {
        wireMockServer.stubFor(post(urlEqualTo(API_DBSCHEMAS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)));
    }
}
