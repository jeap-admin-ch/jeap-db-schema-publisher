package ch.admin.bit.jeap.dbschema.archrepo.client;

import ch.admin.bit.jeap.dbschema.DbSchemaPublisherTestApplication;
import ch.admin.bit.jeap.dbschema.archrepo.client.ArchitectureRepositoryServiceYamlConverterTest.YamlHttpMessageConverterConfiguration;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.yaml.JacksonYamlHttpMessageConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression test for JEAP-7472.
 * <p>
 * A host application may register an {@link org.springframework.http.converter.HttpMessageConverter} bean - the jEAP
 * process archive service does so for its backfill REST API, which consumes YAML. Spring Boot hands such beans to the
 * client converters as well, where they are added <em>ahead of the default converters</em>. As the architecture
 * repository client is built from the application's shared {@code RestClient.Builder}, a request without an explicit
 * content type would then be written by the YAML converter and rejected by archrepo with HTTP 415.
 */
@SpringBootTest(classes = {DbSchemaPublisherTestApplication.class, YamlHttpMessageConverterConfiguration.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@ActiveProfiles("test")
class ArchitectureRepositoryServiceYamlConverterTest {

    private static final String API_DBSCHEMAS_PATH = "/api/dbschemas";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_YAML = "application/yaml";
    private static final String TEST_APP = "test-app";

    static WireMockServer wireMockServer = new WireMockServer(wireMockConfig()
            .dynamicPort()
            .http2PlainDisabled(true));

    @Autowired
    private ArchitectureRepositoryService architectureRepositoryService;

    @Autowired
    private RestClient.Builder restClientBuilder;

    /**
     * Mirrors a host application registering a YAML converter bean, as
     * {@code ch.admin.bit.jeap.processarchive.adapter.restapi.config.RestApiAdapterConfig} does.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class YamlHttpMessageConverterConfiguration {

        @Bean
        JacksonYamlHttpMessageConverter yamlHttpMessageConverter() {
            return new JacksonYamlHttpMessageConverter();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer.start();
        registry.add("wiremock.port", () -> wireMockServer.port());
        registry.add("jeap.archrepo.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @BeforeEach
    void setUpStubs() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)
                        .withBody("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        wireMockServer.stubFor(post(urlEqualTo(API_DBSCHEMAS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)));
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void publishDbSchema_whenApplicationRegistersYamlConverter_thenSchemaIsPublishedAsJson() {
        CreateOrUpdateDbSchemaDto dto = new CreateOrUpdateDbSchemaDto(TEST_APP, createTestDatabaseModel());

        assertThatCode(() -> architectureRepositoryService.publishDbSchema(dto)).doesNotThrowAnyException();

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(API_DBSCHEMAS_PATH)));
        assertThat(requests).hasSize(1);

        var request = requests.getFirst();
        assertThat(request.getHeader(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON);

        String requestBody = request.getBodyAsString();
        assertThatCode(() -> JsonMapper.builder().build().readTree(requestBody)).doesNotThrowAnyException();
        assertThat(requestBody)
                .contains("\"systemComponentName\":\"" + TEST_APP + "\"")
                .contains("\"tables\"")
                .contains("\"users\"");
    }

    /**
     * Control test: proves the YAML converter bean really does take precedence over the JSON converter in the shared
     * {@code RestClient.Builder} of this context. Without it, the test above could pass for the wrong reason.
     */
    @Test
    void sharedRestClientBuilder_whenNoContentTypeIsDeclared_thenYamlConverterWins() {
        CreateOrUpdateDbSchemaDto dto = new CreateOrUpdateDbSchemaDto(TEST_APP, createTestDatabaseModel());

        restClientBuilder.clone()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .build()
                .post()
                .uri(API_DBSCHEMAS_PATH)
                .body(dto)
                .retrieve()
                .toBodilessEntity();

        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo(API_DBSCHEMAS_PATH)));
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().getHeader(CONTENT_TYPE_HEADER)).startsWith(APPLICATION_YAML);
    }

    private static DatabaseSchema createTestDatabaseModel() {
        TableColumn idColumn = new TableColumn("id", "bigint", false);
        TableColumn nameColumn = new TableColumn("name", "varchar(100)", false);
        TablePrimaryKey primaryKey = new TablePrimaryKey("users_pk", List.of("id"));
        Table usersTable = new Table("users", List.of(idColumn, nameColumn), List.of(), primaryKey);
        return new DatabaseSchema("testdb", "1.0", List.of(usersTable));
    }
}
