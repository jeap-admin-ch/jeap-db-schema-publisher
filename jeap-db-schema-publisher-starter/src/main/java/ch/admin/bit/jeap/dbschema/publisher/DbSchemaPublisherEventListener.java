package ch.admin.bit.jeap.dbschema.publisher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Slf4j
class DbSchemaPublisherEventListener {
    private final DbSchemaPublisher dbSchemaPublisher;

    public DbSchemaPublisherEventListener(DbSchemaPublisher dbSchemaPublisher) {
        this.dbSchemaPublisher = dbSchemaPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishSchemaOnStartup() {
        dbSchemaPublisher.publishDatabaseSchemaAsync();
    }

}
