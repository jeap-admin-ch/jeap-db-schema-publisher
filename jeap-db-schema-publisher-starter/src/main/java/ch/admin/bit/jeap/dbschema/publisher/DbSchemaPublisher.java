package ch.admin.bit.jeap.dbschema.publisher;

import ch.admin.bit.jeap.dbschema.archrepo.client.ArchitectureRepositoryService;
import ch.admin.bit.jeap.dbschema.archrepo.client.CreateOrUpdateDbSchemaDto;
import ch.admin.bit.jeap.dbschema.model.DatabaseSchema;
import ch.admin.bit.jeap.dbschema.reader.DatabaseModelReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.scheduling.annotation.Async;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

@Slf4j
class DbSchemaPublisher {

    static final String DB_SCHEMA_PUBLISHER_TASK_EXECUTOR = "dbSchemaPublisherTaskExecutor";

    private static final String TIMER_NAME = "jeap-publish-database-schema";
    private static final String SPAN_NAME = "publish-db-schema";

    private final String applicationName;
    private final ArchRepoProperties properties;
    private final ArchitectureRepositoryService architectureRepositoryService;
    private final DataSource dataSource;
    private final DatabaseModelReader databaseModelReader;
    private final BuildProperties buildProperties;
    private final GitProperties gitProperties;
    private final TracingTimer tracingTimer;

    DbSchemaPublisher(String applicationName,
                      ArchRepoProperties properties,
                      ArchitectureRepositoryService architectureRepositoryService,
                      DataSource dataSource,
                      DatabaseModelReader databaseModelReader,
                      BuildProperties buildProperties,
                      GitProperties gitProperties,
                      TracingTimer tracingTimer) {
        this.applicationName = applicationName;
        this.properties = properties;
        this.architectureRepositoryService = architectureRepositoryService;
        this.dataSource = dataSource;
        this.databaseModelReader = databaseModelReader;
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
        this.tracingTimer = tracingTimer;
    }

    @Async(DB_SCHEMA_PUBLISHER_TASK_EXECUTOR)
    public CompletableFuture<Void> publishDatabaseSchemaAsync() {
        return tracingTimer.traceAndTime(SPAN_NAME, TIMER_NAME, () -> {
            try {
                publishDatabaseSchema();
                return CompletableFuture.completedFuture(null);
            } catch (SQLException e) {
                log.error("Failed to read database schema", e);
                return CompletableFuture.failedFuture(e);
            } catch (Exception e) {
                log.error("Failed to publish database schema", e);
                return CompletableFuture.failedFuture(e);
            }
        });
    }

    void publishDatabaseSchema() throws SQLException {
        log.debug("Reading database schema from {} schema", properties.getSchemaName());
        DatabaseSchema databaseSchema = databaseModelReader.readDatabaseModel(
                dataSource,
                properties.getSchemaName(),
                getAppVersion());

        CreateOrUpdateDbSchemaDto dto = new CreateOrUpdateDbSchemaDto(applicationName, databaseSchema);
        log.info("Publishing schema DTO: componentName={}, tableCount={} to {} with client registration {}",
                dto.systemComponentName(), dto.schema().tables().size(), properties.getUrl(), properties.getOauthClient());
        architectureRepositoryService.publishDbSchema(dto);
        log.info("Published database schema successfully");
    }

    private String getAppVersion() {
        if (buildProperties != null) {
            return buildProperties.getVersion();
        }
        if (gitProperties != null) {
            String gitBuildVersion = gitProperties.get("git.build.version");
            if (gitBuildVersion != null) {
                return gitBuildVersion;
            }
        }
        return "na";
    }
}
