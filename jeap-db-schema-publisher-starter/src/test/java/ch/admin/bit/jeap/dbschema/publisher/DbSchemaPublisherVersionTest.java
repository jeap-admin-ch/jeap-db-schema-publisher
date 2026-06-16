package ch.admin.bit.jeap.dbschema.publisher;

import ch.admin.bit.jeap.dbschema.archrepo.client.ArchitectureRepositoryService;
import ch.admin.bit.jeap.dbschema.model.DatabaseSchema;
import ch.admin.bit.jeap.dbschema.model.Table;
import ch.admin.bit.jeap.dbschema.reader.DatabaseModelReader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DbSchemaPublisherVersionTest {

    private static final TracingTimer TRACING_TIMER = new TracingTimer(null, null);
    private static final String APP_NAME = "test-app";
    private static final String BUILD_KEY_BRANCH = "branch";
    private static final String BUILD_KEY_ARTIFACT = "artifact";
    private static final String BUILD_KEY_GROUP = "group";
    private static final String BUILD_KEY_VERSION = "version";
    private static final String BUILD_KEY_COMMIT_ID_ABBREV = "commit.id.abbrev";
    private static final String GROUP_NAME = "ch.admin.bit.jeap";
    private static final String SCHEMA_NAME = "myschema";
    private static final String VERSION_2_1_3 = "2.1.3";
    private static final String VERSION_3_2_1 = "3.2.1";
    private static final String VERSION_2_1_0_ABC1234 = "2.1.0-abc1234";
    private static final String VERSION_1_5_0_RC1 = "1.5.0-RC1";

    @Test
    void shouldUseBuildPropertiesVersionWhenAvailable() throws SQLException {
        // Given
        ArchRepoProperties properties = new ArchRepoProperties();
        properties.getDatabase().setSchemaName("test");

        ArchitectureRepositoryService architectureRepositoryService = mock(ArchitectureRepositoryService.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseModelReader databaseModelReader = mock(DatabaseModelReader.class);

        Properties buildProps = new Properties();
        buildProps.setProperty(BUILD_KEY_VERSION, VERSION_2_1_3);
        buildProps.setProperty(BUILD_KEY_GROUP, GROUP_NAME);
        buildProps.setProperty(BUILD_KEY_ARTIFACT, APP_NAME);
        BuildProperties buildProperties = new BuildProperties(buildProps);

        DatabaseSchema expectedModel = new DatabaseSchema("test", VERSION_2_1_3, List.of());
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq("test"), any()))
                .thenReturn(expectedModel);

        DbSchemaPublisher publisher = new DbSchemaPublisher(APP_NAME, properties, architectureRepositoryService, dataSource, databaseModelReader, new AppVersionProvider(buildProperties, null), TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify that the version from BuildProperties was used
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq(VERSION_2_1_3));
    }

    @Test
    void shouldUseFallbackVersionWhenBuildPropertiesUnavailable() throws SQLException {
        // Given
        ArchRepoProperties properties = new ArchRepoProperties();
        properties.getDatabase().setSchemaName("test");

        ArchitectureRepositoryService architectureRepositoryService = mock(ArchitectureRepositoryService.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseModelReader databaseModelReader = mock(DatabaseModelReader.class);

        DatabaseSchema expectedModel = new DatabaseSchema("test", "na", List.of());
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq("test"), any()))
                .thenReturn(expectedModel);

        DbSchemaPublisher publisher = new DbSchemaPublisher(APP_NAME, properties, architectureRepositoryService, dataSource, databaseModelReader, new AppVersionProvider(null, null), TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify that the fallback version "na" was used
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq("na"));
    }

    @Test
    void shouldUseVersionFromBuildPropertiesInDatabaseModel() throws SQLException {
        // Given
        ArchRepoProperties properties = new ArchRepoProperties();
        properties.getDatabase().setSchemaName(SCHEMA_NAME);

        ArchitectureRepositoryService architectureRepositoryService = mock(ArchitectureRepositoryService.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseModelReader databaseModelReader = mock(DatabaseModelReader.class);

        Properties buildProps = new Properties();
        buildProps.setProperty(BUILD_KEY_VERSION, VERSION_1_5_0_RC1);
        buildProps.setProperty(BUILD_KEY_GROUP, GROUP_NAME);
        buildProps.setProperty(BUILD_KEY_ARTIFACT, "my-service");
        BuildProperties buildProperties = new BuildProperties(buildProps);

        Table mockTable = Table.builder()
                .name("test_table")
                .columns(List.of())
                .primaryKey(null)
                .foreignKeys(List.of())
                .build();

        DatabaseSchema mockModel = new DatabaseSchema(SCHEMA_NAME, VERSION_1_5_0_RC1, List.of(mockTable));
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq(SCHEMA_NAME), any()))
                .thenReturn(mockModel);

        DbSchemaPublisher publisher = new DbSchemaPublisher(APP_NAME, properties, architectureRepositoryService, dataSource, databaseModelReader, new AppVersionProvider(buildProperties, null), TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify the version was correctly passed to the database model reader
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq(VERSION_1_5_0_RC1));
    }

    @Test
    void shouldUseGitPropertiesVersionWhenBuildPropertiesUnavailable() throws SQLException {
        // Given
        ArchRepoProperties properties = new ArchRepoProperties();
        properties.getDatabase().setSchemaName("test");

        ArchitectureRepositoryService architectureRepositoryService = mock(ArchitectureRepositoryService.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseModelReader databaseModelReader = mock(DatabaseModelReader.class);

        Properties gitProps = new Properties();
        gitProps.setProperty("git.build.version", VERSION_2_1_0_ABC1234);
        gitProps.setProperty(BUILD_KEY_COMMIT_ID_ABBREV, "abc1234");
        gitProps.setProperty(BUILD_KEY_BRANCH, "main");
        GitProperties gitProperties = new GitProperties(gitProps);

        DatabaseSchema expectedModel = new DatabaseSchema("test", VERSION_2_1_0_ABC1234, List.of());
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq("test"), any()))
                .thenReturn(expectedModel);

        DbSchemaPublisher publisher = new DbSchemaPublisher(APP_NAME, properties, architectureRepositoryService, dataSource, databaseModelReader, new AppVersionProvider(null, gitProperties), TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify that the version from GitProperties was used
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq(VERSION_2_1_0_ABC1234));
    }

    @Test
    void shouldPreferBuildPropertiesOverGitProperties() throws SQLException {
        // Given
        ArchRepoProperties properties = new ArchRepoProperties();
        properties.getDatabase().setSchemaName("test");

        ArchitectureRepositoryService architectureRepositoryService = mock(ArchitectureRepositoryService.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseModelReader databaseModelReader = mock(DatabaseModelReader.class);

        Properties buildProps = new Properties();
        buildProps.setProperty(BUILD_KEY_VERSION, VERSION_3_2_1);
        buildProps.setProperty(BUILD_KEY_GROUP, GROUP_NAME);
        buildProps.setProperty(BUILD_KEY_ARTIFACT, APP_NAME);
        BuildProperties buildProperties = new BuildProperties(buildProps);

        Properties gitProps = new Properties();
        gitProps.setProperty("git.build.version", "1.5.0-def5678");
        gitProps.setProperty(BUILD_KEY_COMMIT_ID_ABBREV, "def5678");
        gitProps.setProperty(BUILD_KEY_BRANCH, "main");
        GitProperties gitProperties = new GitProperties(gitProps);

        DatabaseSchema expectedModel = new DatabaseSchema("test", VERSION_3_2_1, List.of());
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq("test"), any()))
                .thenReturn(expectedModel);

        DbSchemaPublisher publisher = new DbSchemaPublisher(APP_NAME, properties, architectureRepositoryService, dataSource, databaseModelReader, new AppVersionProvider(buildProperties, gitProperties), TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify that BuildProperties version was used (not GitProperties)
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq(VERSION_3_2_1));
    }

    @Test
    void shouldUseFallbackWhenGitBuildVersionNotAvailable() throws SQLException {
        // Given
        ArchRepoProperties properties = new ArchRepoProperties();
        properties.getDatabase().setSchemaName("test");

        ArchitectureRepositoryService architectureRepositoryService = mock(ArchitectureRepositoryService.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseModelReader databaseModelReader = mock(DatabaseModelReader.class);

        DatabaseSchema expectedModel = new DatabaseSchema("test", "na", List.of());
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq("test"), any()))
                .thenReturn(expectedModel);

        Properties gitProps = new Properties();
        gitProps.setProperty(BUILD_KEY_COMMIT_ID_ABBREV, "abc1234");
        gitProps.setProperty(BUILD_KEY_BRANCH, "main");
        GitProperties gitProperties = new GitProperties(gitProps);

        DbSchemaPublisher publisher = new DbSchemaPublisher(APP_NAME, properties, architectureRepositoryService, dataSource, databaseModelReader, new AppVersionProvider(null, gitProperties), TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify that fallback "na" was used when git.build.version is not available
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq("na"));
    }

}
