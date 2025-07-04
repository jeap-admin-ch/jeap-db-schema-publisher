package ch.admin.bit.jeap.dbschema.model;

import lombok.Builder;

import java.util.List;

@Builder
public record TablePrimaryKey(
        String name,
        List<String> columnNames) {
}
