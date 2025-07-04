package ch.admin.bit.jeap.dbschema.publisher;

import ch.admin.bit.jeap.dbschema.DbSchemaPublisherTestApplication;
import ch.admin.bit.jeap.dbschema.model.DatabaseSchema;
import ch.admin.bit.jeap.dbschema.model.Table;
import ch.admin.bit.jeap.dbschema.model.TableColumn;
import ch.admin.bit.jeap.dbschema.model.TableForeignKey;
import ch.admin.bit.jeap.dbschema.reader.DatabaseModelReader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DbSchemaPublisherTestApplication.class)
@Testcontainers
@ActiveProfiles("test")
class DatabaseSchemaReaderTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabaseModelReader databaseModelReader;

    @MockitoBean
    private DbSchemaPublisherEventListener dbSchemaPublisherEventListener;

    @Test
    void shouldReadDatabaseModel() throws SQLException {
        // When
        DatabaseSchema model = databaseModelReader.readDatabaseModel(dataSource, "data", "1.0");

        // Then
        assertThat(model).isNotNull();
        assertThat(model.name()).isEqualTo("data");
        assertThat(model.version()).isEqualTo("1.0");
        assertThat(model.tables()).isNotNull();

        // Should include all our test tables plus flyway schema history table
        assertThat(model.tables()).hasSizeGreaterThanOrEqualTo(3);

        // Check that our test tables are present
        List<String> tableNames = model.tables().stream()
                .map(Table::name)
                .toList();

        assertThat(tableNames)
                .contains("users")
                .contains("user_profiles")
                .contains("user_sessions");
    }

    @Test
    void shouldReadUsersTableCorrectly() throws Exception {
        // When
        DatabaseSchema model = databaseModelReader.readDatabaseModel(dataSource, "data", "1.0");

        // Then
        Optional<Table> usersTable = model.tables().stream()
                .filter(t -> "users".equals(t.name()))
                .findFirst();

        assertThat(usersTable).isPresent();
        Table users = usersTable.get();

        // Check table structure
        assertThat(users.name()).isEqualTo("users");
        assertThat(users.columns()).isNotNull();
        assertThat(users.columns()).hasSizeGreaterThanOrEqualTo(7); // id, username, email, full_name, is_active, created_at, updated_at

        // Check specific columns
        assertColumnExists(users, "id", "bigserial", false);
        assertColumnExists(users, "username", "varchar", false);
        assertColumnExists(users, "email", "varchar", false);
        assertColumnExists(users, "full_name", "varchar", true);
        assertColumnExists(users, "is_active", "bool", true);

        // Check primary key
        assertThat(users.primaryKey()).isNotNull();
        assertThat(users.primaryKey().columnNames()).isEqualTo(List.of("id"));

        // Users table should not have foreign keys
        assertThat(users.foreignKeys()).isEmpty();
    }

    @Test
    void shouldReadUserProfilesTableCorrectly() throws SQLException {
        // When
        DatabaseSchema model = databaseModelReader.readDatabaseModel(dataSource, "data", "1.0");

        // Then
        Optional<Table> userProfilesTable = model.tables().stream()
                .filter(t -> "user_profiles".equals(t.name()))
                .findFirst();

        assertThat(userProfilesTable).isPresent();
        Table userProfiles = userProfilesTable.get();

        // Check table structure
        assertThat(userProfiles.name()).isEqualTo("user_profiles");
        assertThat(userProfiles.columns()).isNotNull();
        assertThat(userProfiles.columns()).hasSizeGreaterThanOrEqualTo(11); // Updated to include new session reference columns

        // Check specific columns
        assertColumnExists(userProfiles, "id", "bigserial", false);
        assertColumnExists(userProfiles, "user_id", "int8", false);
        assertColumnExists(userProfiles, "bio", "text", true);
        assertColumnExists(userProfiles, "avatar_url", "varchar", true);

        // Check primary key
        assertThat(userProfiles.primaryKey()).isNotNull();
        assertThat(userProfiles.primaryKey().columnNames()).isEqualTo(List.of("id"));

        // Check foreign keys - should now have 2 foreign keys
        assertThat(userProfiles.foreignKeys()).isNotEmpty();
        assertThat(userProfiles.foreignKeys()).hasSize(2);

        // Check the original foreign key to users table
        TableForeignKey userForeignKey = userProfiles.foreignKeys().stream()
                .filter(fk -> "fk_user_profiles_user_id".equals(fk.name()))
                .findFirst()
                .orElseThrow();
        assertThat(userForeignKey.columnNames()).isEqualTo(List.of("user_id"));
        assertThat(userForeignKey.referencedTableName()).isEqualTo("users");
        assertThat(userForeignKey.referencedColumnNames()).isEqualTo(List.of("id"));

        // Check the new foreign key to user_sessions table (composite key)
        TableForeignKey sessionForeignKey = userProfiles.foreignKeys().stream()
                .filter(fk -> "fk_user_profiles_current_session".equals(fk.name()))
                .findFirst()
                .orElseThrow();
        assertThat(sessionForeignKey.columnNames()).containsExactlyInAnyOrder("current_session_user_id", "current_session_id");
        assertThat(sessionForeignKey.referencedTableName()).isEqualTo("user_sessions");
        assertThat(sessionForeignKey.referencedColumnNames()).containsExactlyInAnyOrder("user_id", "session_id");
    }

    @Test
    void shouldReadUserSessionsTableWithCompositePrimaryKey() throws SQLException {
        // When
        DatabaseSchema model = databaseModelReader.readDatabaseModel(dataSource, "data", "1.0");

        // Then
        Optional<Table> userSessionsTable = model.tables().stream()
                .filter(t -> "user_sessions".equals(t.name()))
                .findFirst();

        assertThat(userSessionsTable).isPresent();
        Table userSessions = userSessionsTable.get();

        // Check table structure
        assertThat(userSessions.name()).isEqualTo("user_sessions");
        assertThat(userSessions.columns()).isNotNull();
        assertThat(userSessions.columns()).hasSizeGreaterThanOrEqualTo(9);

        // Check specific columns
        assertColumnExists(userSessions, "user_id", "int8", false);
        assertColumnExists(userSessions, "session_id", "varchar", false);
        assertColumnExists(userSessions, "session_token", "varchar", false);
        assertColumnExists(userSessions, "ip_address", "inet", true);
        assertColumnExists(userSessions, "user_agent", "text", true);
        assertColumnExists(userSessions, "is_active", "bool", true);
        assertColumnExists(userSessions, "expires_at", "timestamptz", false);

        // Check composite primary key - this is the key test for composite keys
        assertThat(userSessions.primaryKey()).isNotNull();
        assertThat(userSessions.primaryKey().columnNames()).containsExactlyInAnyOrder("user_id", "session_id");

        // Check foreign key to users table
        assertThat(userSessions.foreignKeys()).isNotEmpty();
        assertThat(userSessions.foreignKeys()).hasSize(1);

        TableForeignKey foreignKey = userSessions.foreignKeys().getFirst();
        assertThat(foreignKey.name()).isEqualTo("fk_user_sessions_user_id");
        assertThat(foreignKey.columnNames()).isEqualTo(List.of("user_id"));
        assertThat(foreignKey.referencedTableName()).isEqualTo("users");
        assertThat(foreignKey.referencedColumnNames()).isEqualTo(List.of("id"));
    }

    @Test
    void shouldHandleSchemaWithoutTables() throws SQLException {
        // When - reading from a schema that doesn't exist
        DatabaseSchema model = databaseModelReader.readDatabaseModel(dataSource, "nonexistent", "1.0");

        // Then
        assertThat(model).isNotNull();
        assertThat(model.name()).isEqualTo("nonexistent");
        assertThat(model.version()).isEqualTo("1.0");
        assertThat(model.tables()).isEmpty();
    }

    private void assertColumnExists(Table table, String columnName, String expectedType, boolean expectedNullable) {
        Optional<TableColumn> column = table.columns().stream()
                .filter(c -> columnName.equals(c.name()))
                .findFirst();

        assertThat(column)
                .withFailMessage("Column %s should exist in table %s", columnName, table.name())
                .isPresent();

        TableColumn col = column.get();
        assertThat(col.type().toLowerCase())
                .withFailMessage("Column %s should have type containing %s but was %s", columnName, expectedType, col.type())
                .isEqualTo(expectedType.toLowerCase());
        assertThat(col.nullable())
                .withFailMessage("Column %s nullable should be %s", columnName, expectedNullable)
                .isEqualTo(expectedNullable);
    }
}
