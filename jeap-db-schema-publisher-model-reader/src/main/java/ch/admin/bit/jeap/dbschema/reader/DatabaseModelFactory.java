package ch.admin.bit.jeap.dbschema.reader;

import ch.admin.bit.jeap.dbschema.model.Table;
import ch.admin.bit.jeap.dbschema.model.TableColumn;
import ch.admin.bit.jeap.dbschema.model.TableForeignKey;
import ch.admin.bit.jeap.dbschema.model.TablePrimaryKey;
import lombok.extern.slf4j.Slf4j;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
class DatabaseModelFactory {

    List<Table> createTableModels(DatabaseMetaData metaData, String schemaName) throws SQLException {
        List<Table> tables = new ArrayList<>();

        // Get all tables in the schema
        try (ResultSet tablesResultSet = metaData.getTables(null, schemaName, "%", new String[]{"TABLE"})) {
            while (tablesResultSet.next()) {
                String tableName = tablesResultSet.getString("TABLE_NAME");
                String tableSchema = tablesResultSet.getString("TABLE_SCHEM");

                log.debug("Processing table: {}", tableName);

                List<TableColumn> columns = createTableColumns(metaData, tableSchema, tableName);
                TablePrimaryKey primaryKey = createPrimaryKey(metaData, tableSchema, tableName);
                List<TableForeignKey> foreignKeys = createForeignKeys(metaData, tableSchema, tableName);
                Table table = new Table(tableName, columns, foreignKeys, primaryKey);
                tables.add(table);
            }
        }

        log.debug("Collected {} tables", tables.size());
        return tables;
    }

    private List<TableColumn> createTableColumns(DatabaseMetaData metaData, String schemaName, String tableName) throws SQLException {
        List<TableColumn> columns = new ArrayList<>();

        try (ResultSet columnsResultSet = metaData.getColumns(null, schemaName, tableName, null)) {
            while (columnsResultSet.next()) {
                String columnName = columnsResultSet.getString("COLUMN_NAME");
                String typeName = columnsResultSet.getString("TYPE_NAME");
                String isNullable = columnsResultSet.getString("IS_NULLABLE");
                boolean nullable = "YES".equalsIgnoreCase(isNullable);

                TableColumn column = new TableColumn(columnName, typeName, nullable);
                columns.add(column);
            }
        }

        return columns;
    }

    private TablePrimaryKey createPrimaryKey(DatabaseMetaData metaData, String schemaName, String tableName) throws SQLException {
        List<String> columnNames = new ArrayList<>();
        String primaryKeyName = null;

        try (ResultSet primaryKeysResultSet = metaData.getPrimaryKeys(null, schemaName, tableName)) {
            while (primaryKeysResultSet.next()) {
                String columnName = primaryKeysResultSet.getString("COLUMN_NAME");
                String pkName = primaryKeysResultSet.getString("PK_NAME");

                columnNames.add(columnName);
                if (primaryKeyName == null) {
                    primaryKeyName = pkName;
                }
            }
        }

        if (columnNames.isEmpty()) {
            return null; // No primary key
        }

        log.debug("Primary key: {} on columns {}", primaryKeyName, columnNames);

        return new TablePrimaryKey(primaryKeyName, columnNames);
    }

    private List<TableForeignKey> createForeignKeys(DatabaseMetaData metaData, String schemaName, String tableName) throws SQLException {
        Map<String, ForeignKeyBuilder> foreignKeyBuilders = new HashMap<>();

        try (ResultSet foreignKeysResultSet = metaData.getImportedKeys(null, schemaName, tableName)) {
            while (foreignKeysResultSet.next()) {
                String fkName = foreignKeysResultSet.getString("FK_NAME");
                String fkColumnName = foreignKeysResultSet.getString("FKCOLUMN_NAME");
                String pkTableName = foreignKeysResultSet.getString("PKTABLE_NAME");
                String pkColumnName = foreignKeysResultSet.getString("PKCOLUMN_NAME");

                // Group by foreign key name since a FK can span multiple columns
                ForeignKeyBuilder builder = foreignKeyBuilders.computeIfAbsent(fkName,
                        name -> new ForeignKeyBuilder(name, pkTableName));

                builder.addColumnMapping(fkColumnName, pkColumnName);
            }
        }

        return foreignKeyBuilders.values().stream()
                .map(ForeignKeyBuilder::build)
                .peek(foreignKey -> log.debug("Foreign key: {} -> {}.{}", foreignKey.name(), // NOSONAR
                        foreignKey.referencedTableName(), foreignKey.referencedColumnNames()))
                .toList();
    }

    // Helper class to build foreign keys that may span multiple columns
    private static class ForeignKeyBuilder {
        private final String name;
        private final String referencedTableName;
        private final List<String> columnNames = new ArrayList<>();
        private final List<String> referencedColumnNames = new ArrayList<>();

        public ForeignKeyBuilder(String name, String referencedTableName) {
            this.name = name;
            this.referencedTableName = referencedTableName;
        }

        public void addColumnMapping(String localColumn, String referencedColumn) {
            columnNames.add(localColumn);
            referencedColumnNames.add(referencedColumn);
        }

        public TableForeignKey build() {
            return new TableForeignKey(name, columnNames, referencedTableName, referencedColumnNames);
        }
    }
}
