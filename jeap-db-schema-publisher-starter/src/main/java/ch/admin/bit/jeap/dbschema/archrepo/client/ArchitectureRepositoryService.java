package ch.admin.bit.jeap.dbschema.archrepo.client;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

public interface ArchitectureRepositoryService {

    /**
     * Publishes the database schema to the architecture repository.
     * <p>
     * The content type is declared explicitly on purpose: the {@code RestClient} backing this HTTP interface is derived
     * from the application's shared {@code RestClient.Builder}. Every {@code HttpMessageConverter} bean registered by
     * the host application is added ahead of the default converters, so without an explicit content type the first
     * converter able to write the body would win. A host-registered YAML converter would therefore send
     * {@code application/yaml}, which the architecture repository rejects with HTTP 415.
     */
    @PostExchange(value = "/api/dbschemas", contentType = MediaType.APPLICATION_JSON_VALUE)
    void publishDbSchema(@RequestBody CreateOrUpdateDbSchemaDto dto);
}
