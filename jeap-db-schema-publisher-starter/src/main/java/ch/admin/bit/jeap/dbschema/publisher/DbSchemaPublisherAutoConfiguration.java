package ch.admin.bit.jeap.dbschema.publisher;

import brave.Tracer;
import ch.admin.bit.jeap.dbschema.archrepo.client.ArchitectureRepositoryService;
import ch.admin.bit.jeap.dbschema.archrepo.client.OAuth2ClientCredentialsRestClientInitializer;
import ch.admin.bit.jeap.dbschema.reader.DatabaseModelReader;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import javax.sql.DataSource;

/**
 * Enabling the DB schema upload to the architecture repository (archrepo) requires setting the property
 * <pre>jeap.archrepo.url</pre> to the URL of the archrepo service.
 * This autoconfiguration is can be completely disabled (for example in tests) by setting the property <pre>jeap.archrepo.enabled=false</pre>.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(ArchRepoProperties.class)
@ConditionalOnProperty(prefix = ArchRepoProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableAsync
public class DbSchemaPublisherAutoConfiguration {

    @Bean
    public DatabaseModelReader databaseModelReader() {
        return new DatabaseModelReader();
    }

    @Bean
    @ConditionalOnProperty(prefix = ArchRepoProperties.PREFIX, name = "url")
    public ArchitectureRepositoryService architectureRepositoryService(ClientRegistrationRepository clientRegistrationRepository,
                                                                       OAuth2AuthorizedClientService clientService,
                                                                       RestClient.Builder builder,
                                                                       ArchRepoProperties properties) {

        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId(properties.getOauthClient());
        if (clientRegistration == null) {
            throw new IllegalStateException("No OAuth2 client registration found with id: " + properties.getOauthClient() +
                    ". Please ensure that the client registration is configured correctly at jeap.archrepo.oauth-client and that " +
                    "an oauth client has been registered in the spring security configuration at spring.security.oauth2.client.registration." + properties.getOauthClient());
        }

        OAuth2ClientCredentialsRestClientInitializer initializer =
                new OAuth2ClientCredentialsRestClientInitializer(
                        authorizedClientManager(clientRegistrationRepository, clientService),
                        clientRegistration);

        RestClient restClient = builder.clone()
                .baseUrl(properties.getUrl())
                .requestInitializer(initializer)
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(ArchitectureRepositoryService.class);
    }

    private OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                                  OAuth2AuthorizedClientService clientService) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, clientService);
        OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        return authorizedClientManager;
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ArchitectureRepositoryService.class})
    public DbSchemaPublisher dbSchemaPublisher(ArchRepoProperties properties,
                                               ArchitectureRepositoryService architectureRepositoryService,
                                               DataSource dataSource,
                                               DatabaseModelReader databaseModelReader,
                                               @Value("${spring.application.name}") String applicationName,
                                               @Autowired(required = false) BuildProperties buildProperties,
                                               @Autowired(required = false) GitProperties gitProperties,
                                               @Autowired(required = false) Tracer tracer,
                                               @Autowired(required = false) MeterRegistry meterRegistry) {
        return new DbSchemaPublisher(applicationName, properties, architectureRepositoryService,
                dataSource, databaseModelReader, buildProperties, gitProperties, new TracingTimer(tracer, meterRegistry));
    }

    @Bean
    @ConditionalOnBean(DbSchemaPublisher.class)
    public DbSchemaPublisherEventListener dbSchemaPublisherEventListener(DbSchemaPublisher dbSchemaPublisher) {
        return new DbSchemaPublisherEventListener(dbSchemaPublisher);
    }

    @Bean(DbSchemaPublisher.DB_SCHEMA_PUBLISHER_TASK_EXECUTOR)
    @ConditionalOnBean(DbSchemaPublisher.class)
    public ThreadPoolTaskExecutor dbSchemaPublisherTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(0);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("db-schema-publisher-");
        executor.initialize();
        return executor;
    }
}
