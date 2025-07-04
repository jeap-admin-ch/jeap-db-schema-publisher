package ch.admin.bit.jeap.dbschema.archrepo.client;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

public interface ArchitectureRepositoryService {

    @PostExchange("/api/dbschemas")
    void publishDbSchema(@RequestBody CreateOrUpdateDbSchemaDto dto);
}
