package ch.admin.bit.jeap.dbschema.archrepo.client;

import ch.admin.bit.jeap.dbschema.DbSchemaPublisherTestApplication;
import ch.admin.bit.jeap.dbschema.archrepo.client.ArchitectureRepositoryServiceYamlConverterTest.YamlHttpMessageConverterConfiguration;
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

import static ch.admin.bit.jeap.dbschema.archrepo.client.ArchRepoTestFixtures.*;
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

    private static final String APPLICATION_YAML = "application/yaml";

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
        stubOAuthTokenEndpoint(wireMockServer);
        stubDbSchemasEndpoint(wireMockServer);
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void publishDbSchema_whenApplicationRegistersYamlConverter_thenSchemaIsPublishedAsJson() {
        CreateOrUpdateDbSchemaDto dto = new CreateOrUpdateDbSchemaDto(TEST_APP, testDatabaseSchema());

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
     * <p>
     * It asserts Spring Boot behaviour rather than behaviour of this library. If it fails after a Spring Boot upgrade,
     * the framework's converter precedence changed - check whether the explicit content type on
     * {@link ArchitectureRepositoryService#publishDbSchema} is still needed before removing either.
     */
    @Test
    void sharedRestClientBuilder_whenNoContentTypeIsDeclared_thenYamlConverterWins() {
        CreateOrUpdateDbSchemaDto dto = new CreateOrUpdateDbSchemaDto(TEST_APP, testDatabaseSchema());

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
        assertThat(requests.getFirst().getHeader(CONTENT_TYPE_HEADER))
                .withFailMessage("Expected a host-registered YAML converter to take precedence over the JSON " +
                        "converter. If this fails after a Spring Boot upgrade, the framework's message converter " +
                        "precedence changed - verify whether the explicit content type on publishDbSchema is still " +
                        "required.")
                .startsWith(APPLICATION_YAML);
    }
}
