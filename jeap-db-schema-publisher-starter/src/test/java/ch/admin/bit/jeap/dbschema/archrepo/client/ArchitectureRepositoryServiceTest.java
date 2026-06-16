package ch.admin.bit.jeap.dbschema.archrepo.client;

import ch.admin.bit.jeap.dbschema.DbSchemaPublisherTestApplication;
import ch.admin.bit.jeap.dbschema.model.DatabaseSchema;
import ch.admin.bit.jeap.dbschema.model.Table;
import ch.admin.bit.jeap.dbschema.model.TableColumn;
import ch.admin.bit.jeap.dbschema.model.TablePrimaryKey;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = DbSchemaPublisherTestApplication.class)
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@ActiveProfiles("test")
class ArchitectureRepositoryServiceTest {

    private static final String API_DBSCHEMAS_PATH = "/api/dbschemas";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String EXPECTED_ONE_API_CALL_MESSAGE = "Expected exactly one API call";
    private static final String TEST_APP = "test-app";
    private static final String BIGINT = "bigint";

    static WireMockServer wireMockServer = new WireMockServer(wireMockConfig()
            .dynamicPort()
            .http2PlainDisabled(true));

    @Autowired
    private ArchitectureRepositoryService architectureRepositoryService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer.start();

        // Set up mock API endpoint before Spring Boot starts
        wireMockServer.stubFor(post(urlEqualTo(API_DBSCHEMAS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)));

        registry.add("wiremock.port", () -> wireMockServer.port());
        registry.add("jeap.archrepo.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
        mockOAuthTokenResponse();
    }

    @AfterAll
    static void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void shouldPublishDbSchemaSuccessfully() {
        // Given
        DatabaseSchema databaseSchema = createTestDatabaseModel();
        CreateOrUpdateDbSchemaDto dto = new CreateOrUpdateDbSchemaDto(
                TEST_APP,
                databaseSchema
        );

        // Set up WireMock stub
        wireMockServer.stubFor(post(urlEqualTo(API_DBSCHEMAS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)));

        // When
        assertDoesNotThrow(() -> architectureRepositoryService.publishDbSchema(dto));

        // Then
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(API_DBSCHEMAS_PATH)));
        assertThat(requests)
                .withFailMessage(EXPECTED_ONE_API_CALL_MESSAGE)
                .hasSize(1);

        var request = requests.get(0);
        assertThat(request.getHeader(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON);

        // Verify request body contains expected JSON structure
        String requestBody = request.getBodyAsString();
        assertThat(requestBody)
                .contains("\"systemComponentName\":\"" + TEST_APP + "\"")
                .contains("\"schema\"")
                .contains("\"tables\"")
                .contains("\"users\"");
    }

    private static void mockOAuthTokenResponse() {
        // Mock OAuth2 token endpoint
        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .withBody("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }

    @Test
    void shouldHandleServerError() {
        // Given
        DatabaseSchema databaseSchema = createTestDatabaseModel();
        CreateOrUpdateDbSchemaDto dto = new CreateOrUpdateDbSchemaDto(
                TEST_APP,
                databaseSchema
        );

        // Set up WireMock stub for server error
        wireMockServer.stubFor(post(urlEqualTo(API_DBSCHEMAS_PATH))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)));

        // When & Then
        assertThrows(HttpServerErrorException.class,
                () -> architectureRepositoryService.publishDbSchema(dto));

        // Verify request was made
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(API_DBSCHEMAS_PATH)));
        assertThat(requests)
                .withFailMessage(EXPECTED_ONE_API_CALL_MESSAGE)
                .hasSize(1);
    }

    @Test
    void shouldHandleClientError() {
        // Given
        DatabaseSchema databaseSchema = createTestDatabaseModel();
        CreateOrUpdateDbSchemaDto dto = new CreateOrUpdateDbSchemaDto(
                TEST_APP,
                databaseSchema
        );

        // Set up WireMock stub for client error
        wireMockServer.stubFor(post(urlEqualTo(API_DBSCHEMAS_PATH))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)));

        // When & Then
        assertThrows(HttpClientErrorException.class,
                () -> architectureRepositoryService.publishDbSchema(dto));

        // Verify request was made
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(API_DBSCHEMAS_PATH)));
        assertThat(requests)
                .withFailMessage(EXPECTED_ONE_API_CALL_MESSAGE)
                .hasSize(1);
    }

    @Test
    void shouldSerializeComplexDatabaseSchema() {
        // Given
        DatabaseSchema databaseSchema = createComplexDatabaseModel();
        CreateOrUpdateDbSchemaDto dto = new CreateOrUpdateDbSchemaDto(
                "complex-component",
                databaseSchema
        );

        // Set up WireMock stub
        wireMockServer.stubFor(post(urlEqualTo(API_DBSCHEMAS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .withBody("{\"status\":\"success\"}")));

        // When
        assertDoesNotThrow(() -> architectureRepositoryService.publishDbSchema(dto));

        // Then
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(API_DBSCHEMAS_PATH)));
        assertThat(requests)
                .withFailMessage(EXPECTED_ONE_API_CALL_MESSAGE)
                .hasSize(1);

        String requestBody = requests.getFirst().getBodyAsString();

        // Verify complex schema is properly serialized
        assertThat(requestBody)
                .contains("\"systemComponentName\":\"complex-component\"")
                .contains("\"name\":\"testdb\"")
                .contains("\"version\":\"2.0\"")
                .contains("\"users\"")
                .contains("\"orders\"")
                .contains("\"primaryKey\"")
                .contains("\"foreignKeys\"");
    }

    private DatabaseSchema createTestDatabaseModel() {
        TableColumn idColumn = new TableColumn("id", BIGINT, false);
        TableColumn nameColumn = new TableColumn("name", "varchar(100)", false);

        TablePrimaryKey primaryKey = new TablePrimaryKey("users_pk", List.of("id"));

        Table usersTable = new Table(
                "users",
                List.of(idColumn, nameColumn),
                List.of(), // no foreign keys
                primaryKey
        );

        return new DatabaseSchema("testdb", "1.0", List.of(usersTable));
    }

    private DatabaseSchema createComplexDatabaseModel() {
        // Users table
        TableColumn userIdColumn = new TableColumn("id", BIGINT, false);
        TableColumn userNameColumn = new TableColumn("name", "varchar(100)", false);
        TableColumn userEmailColumn = new TableColumn("email", "varchar(255)", true);

        TablePrimaryKey usersPrimaryKey = new TablePrimaryKey("users_pk", List.of("id"));

        Table usersTable = new Table(
                "users",
                List.of(userIdColumn, userNameColumn, userEmailColumn),
                List.of(), // no foreign keys
                usersPrimaryKey
        );

        // Orders table
        TableColumn orderIdColumn = new TableColumn("id", BIGINT, false);
        TableColumn orderUserIdColumn = new TableColumn("user_id", BIGINT, false);
        TableColumn orderAmountColumn = new TableColumn("amount", "decimal(10,2)", false);

        TablePrimaryKey ordersPrimaryKey = new TablePrimaryKey("orders_pk", List.of("id"));

        Table ordersTable = new Table(
                "orders",
                List.of(orderIdColumn, orderUserIdColumn, orderAmountColumn),
                List.of(), // simplified - no foreign keys in this test
                ordersPrimaryKey
        );

        return new DatabaseSchema("testdb", "2.0", List.of(usersTable, ordersTable));
    }
}
