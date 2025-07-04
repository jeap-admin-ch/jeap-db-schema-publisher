package ch.admin.bit.jeap.dbschema.model;

import lombok.Builder;

import java.util.List;

@Builder
public record TableForeignKey(
        String name,
        List<String> columnNames,
        String referencedTableName,
        List<String> referencedColumnNames) {
}
