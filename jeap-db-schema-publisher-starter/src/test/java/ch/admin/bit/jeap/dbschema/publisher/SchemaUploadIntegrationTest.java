package ch.admin.bit.jeap.dbschema.publisher;

import ch.admin.bit.jeap.dbschema.DbSchemaPublisherTestApplication;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = DbSchemaPublisherTestApplication.class)
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureObservability // To test the timed annotation on the publisher method
class SchemaUploadIntegrationTest {

    @Autowired
    private DbSchemaPublisherEventListener dbSchemaPublisherEventListener;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoSpyBean(name = DbSchemaPublisher.DB_SCHEMA_PUBLISHER_TASK_EXECUTOR)
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16")
            .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    static WireMockServer wireMockServer = new WireMockServer(wireMockConfig()
            .dynamicPort()
            .http2PlainDisabled(true));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer.start();

        mockOAuthTokenResponse();

        // Set up mock API endpoint before Spring Boot starts
        wireMockServer.stubFor(post(urlEqualTo("/api/dbschemas"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        registry.add("wiremock.port", () -> wireMockServer.port());
        registry.add("jeap.archrepo.url", () -> "http://localhost:" + wireMockServer.port());
    }

    @AfterAll
    static void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void shouldUploadSchemaOnStartup() {
        // Wait for async publication to complete (since it's triggered by ApplicationReadyEvent)
        await()
                .atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> {
                    var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/api/dbschemas")));
                    assertThat(requests)
                            .withFailMessage("Expected at least one API call to /api/dbschemas")
                            .hasSizeGreaterThan(0);
                });

        // Verify the request was made
        var requests = wireMockServer.findAll(postRequestedFor(urlEqualTo("/api/dbschemas")));
        assertThat(requests)
                .withFailMessage("Expected one API call to /api/dbschemas")
                .hasSize(1);

        LoggedRequest request = requests.getFirst();

        // Verify that the request has a bearer token in the auth header
        assertThat(request.getHeader("Authorization"))
                .withFailMessage("Request should contain Authorization header with Bearer token")
                .isNotNull()
                .contains("Bearer test-token");

        String requestBody = request.getBodyAsString();

        // Verify basic JSON structure
        assertThat(requestBody)
                .withFailMessage("Request should contain systemComponentName field")
                .contains("systemComponentName");
        assertThat(requestBody)
                .withFailMessage("Request should contain schema field")
                .contains("schema");

        // Verify system configuration values
        assertThat(requestBody)
                .withFailMessage("Request should contain test-app value")
                .contains("test-app");

        // Verify database schema content - be more flexible with table names since JSON structure may vary
        assertThat(requestBody)
                .withFailMessage("Request should contain tables")
                .contains("tables");
        assertThat(requestBody)
                .withFailMessage("Request should contain users table")
                .contains("users");
        assertThat(requestBody)
                .withFailMessage("Request should contain user_profiles table")
                .contains("user_profiles");

        // Verify that the task was indeed executed asynchronously
        Mockito.verify(threadPoolTaskExecutor, Mockito.times(1))
                .execute(Mockito.any(Runnable.class));

        Timer timer = (Timer) meterRegistry.getMeters().stream().filter(t -> t.getId().getName().contains("jeap-publish-database-schema"))
                .toList().getFirst();
        assertThat(timer.count())
                .withFailMessage("Expected timer for jeap-publish-database-schema to be recorded")
                .isOne();
    }

    private static void mockOAuthTokenResponse() {
        // Mock OAuth2 token endpoint
        wireMockServer.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
    }
}
