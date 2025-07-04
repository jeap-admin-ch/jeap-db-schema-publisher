package ch.admin.bit.jeap.dbschema.archrepo.client;

import ch.admin.bit.jeap.dbschema.model.DatabaseSchema;

public record CreateOrUpdateDbSchemaDto(String systemComponentName, DatabaseSchema schema) {
}

