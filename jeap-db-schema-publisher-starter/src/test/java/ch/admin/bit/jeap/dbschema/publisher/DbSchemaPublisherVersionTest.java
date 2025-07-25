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

    private final static TracingTimer TRACING_TIMER = new TracingTimer(null, null);

    @Test
    void shouldUseBuildPropertiesVersionWhenAvailable() throws SQLException {
        // Given
        ArchRepoProperties properties = new ArchRepoProperties();
        properties.getDatabase().setSchemaName("test");

        ArchitectureRepositoryService architectureRepositoryService = mock(ArchitectureRepositoryService.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseModelReader databaseModelReader = mock(DatabaseModelReader.class);

        Properties buildProps = new Properties();
        buildProps.setProperty("version", "2.1.3");
        buildProps.setProperty("group", "ch.admin.bit.jeap");
        buildProps.setProperty("artifact", "test-app");
        BuildProperties buildProperties = new BuildProperties(buildProps);

        DatabaseSchema expectedModel = new DatabaseSchema("test", "2.1.3", List.of());
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq("test"), any()))
                .thenReturn(expectedModel);

        DbSchemaPublisher publisher = new DbSchemaPublisher("test-app", properties, architectureRepositoryService, dataSource, databaseModelReader, buildProperties, null, TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify that the version from BuildProperties was used
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq("2.1.3"));
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

        DbSchemaPublisher publisher = new DbSchemaPublisher("test-app", properties, architectureRepositoryService, dataSource, databaseModelReader, null, null, TRACING_TIMER);

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
        properties.getDatabase().setSchemaName("myschema");

        ArchitectureRepositoryService architectureRepositoryService = mock(ArchitectureRepositoryService.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseModelReader databaseModelReader = mock(DatabaseModelReader.class);

        Properties buildProps = new Properties();
        buildProps.setProperty("version", "1.5.0-RC1");
        buildProps.setProperty("group", "ch.admin.bit.jeap");
        buildProps.setProperty("artifact", "my-service");
        BuildProperties buildProperties = new BuildProperties(buildProps);

        Table mockTable = Table.builder()
                .name("test_table")
                .columns(List.of())
                .primaryKey(null)
                .foreignKeys(List.of())
                .build();

        DatabaseSchema mockModel = new DatabaseSchema("myschema", "1.5.0-RC1", List.of(mockTable));
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq("myschema"), any()))
                .thenReturn(mockModel);

        DbSchemaPublisher publisher = new DbSchemaPublisher("test-app", properties, architectureRepositoryService, dataSource, databaseModelReader, buildProperties, null, TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify the version was correctly passed to the database model reader
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq("1.5.0-RC1"));
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
        gitProps.setProperty("git.build.version", "2.1.0-abc1234");
        gitProps.setProperty("commit.id.abbrev", "abc1234");
        gitProps.setProperty("branch", "main");
        GitProperties gitProperties = new GitProperties(gitProps);

        DatabaseSchema expectedModel = new DatabaseSchema("test", "2.1.0-abc1234", List.of());
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq("test"), any()))
                .thenReturn(expectedModel);

        DbSchemaPublisher publisher = new DbSchemaPublisher("test-app", properties, architectureRepositoryService, dataSource, databaseModelReader, null, gitProperties, TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify that the version from GitProperties was used
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq("2.1.0-abc1234"));
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
        buildProps.setProperty("version", "3.2.1");
        buildProps.setProperty("group", "ch.admin.bit.jeap");
        buildProps.setProperty("artifact", "test-app");
        BuildProperties buildProperties = new BuildProperties(buildProps);

        Properties gitProps = new Properties();
        gitProps.setProperty("git.build.version", "1.5.0-def5678");
        gitProps.setProperty("commit.id.abbrev", "def5678");
        gitProps.setProperty("branch", "main");
        GitProperties gitProperties = new GitProperties(gitProps);

        DatabaseSchema expectedModel = new DatabaseSchema("test", "3.2.1", List.of());
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq("test"), any()))
                .thenReturn(expectedModel);

        DbSchemaPublisher publisher = new DbSchemaPublisher("test-app", properties, architectureRepositoryService, dataSource, databaseModelReader, buildProperties, gitProperties, TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify that BuildProperties version was used (not GitProperties)
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq("3.2.1"));
    }

    @Test
    void shouldUseFallbackWhenGitBuildVersionNotAvailable() throws SQLException {
        // Given
        ArchRepoProperties properties = new ArchRepoProperties();
        properties.getDatabase().setSchemaName("test");

        ArchitectureRepositoryService architectureRepositoryService = mock(ArchitectureRepositoryService.class);
        DataSource dataSource = mock(DataSource.class);
        DatabaseModelReader databaseModelReader = mock(DatabaseModelReader.class);

        Properties gitProps = new Properties();
        gitProps.setProperty("commit.id.abbrev", "xyz9876");
        gitProps.setProperty("branch", "main");
        // Note: no git.build.version property
        GitProperties gitProperties = new GitProperties(gitProps);

        DatabaseSchema expectedModel = new DatabaseSchema("test", "na", List.of());
        when(databaseModelReader.readDatabaseModel(eq(dataSource), eq("test"), any()))
                .thenReturn(expectedModel);

        DbSchemaPublisher publisher = new DbSchemaPublisher("test-app", properties, architectureRepositoryService, dataSource, databaseModelReader, null, null, TRACING_TIMER);

        // When
        publisher.publishDatabaseSchemaAsync().join();

        // Then - verify that fallback "na" was used when git.build.version is not available
        verify(databaseModelReader)
                .readDatabaseModel(any(), any(), eq("na"));
    }

}
