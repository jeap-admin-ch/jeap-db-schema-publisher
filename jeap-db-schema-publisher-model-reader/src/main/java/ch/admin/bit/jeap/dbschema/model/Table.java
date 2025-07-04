package ch.admin.bit.jeap.dbschema.model;

import lombok.Builder;

import java.util.List;

@Builder
public record Table(
        String name,
        List<TableColumn> columns,
        List<TableForeignKey> foreignKeys,
        TablePrimaryKey primaryKey) {
}
