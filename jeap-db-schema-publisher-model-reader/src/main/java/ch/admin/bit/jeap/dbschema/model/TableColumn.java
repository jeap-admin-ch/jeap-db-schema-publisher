package ch.admin.bit.jeap.dbschema.model;

import lombok.Builder;

@Builder
public record TableColumn(
        String name,
        String type,
        boolean nullable) {
}
