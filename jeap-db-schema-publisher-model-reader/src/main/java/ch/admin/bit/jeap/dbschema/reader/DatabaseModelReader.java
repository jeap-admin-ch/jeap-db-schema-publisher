package ch.admin.bit.jeap.dbschema.reader;

import ch.admin.bit.jeap.dbschema.model.DatabaseSchema;
import ch.admin.bit.jeap.dbschema.model.Table;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

@Slf4j
public class DatabaseModelReader {

    private final DatabaseModelFactory databaseModelFactory = new DatabaseModelFactory();

    public DatabaseSchema readDatabaseModel(DataSource dataSource, String schemaName, String version) throws SQLException {
        log.info("Reading database model from schema: {}", schemaName);

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            List<Table> tables = databaseModelFactory.createTableModels(metaData, schemaName);

            return new DatabaseSchema(schemaName, version, tables);
        }
    }
}
