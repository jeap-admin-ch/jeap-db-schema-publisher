package ch.admin.bit.jeap.dbschema.model;

import lombok.Builder;

import java.util.List;

@Builder
public record DatabaseSchema(
        String name,
        String version,
        List<Table> tables) {
}
