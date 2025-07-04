package ch.admin.bit.jeap.dbschema.publisher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

@Slf4j
class DbSchemaPublisherEventListener {

    private final DbSchemaPublisher dbSchemaPublisher;

    public DbSchemaPublisherEventListener(DbSchemaPublisher dbSchemaPublisher) {
        this.dbSchemaPublisher = dbSchemaPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishSchemaOnStartup() {
        publishSchemaAsync();
    }

    @Async("dbSchemaPublisherTaskExecutor")
    public CompletableFuture<Void> publishSchemaAsync() {
        try {
            dbSchemaPublisher.publishDatabaseSchema();
            return CompletableFuture.completedFuture(null);

        } catch (SQLException e) {
            log.error("Failed to read database schema", e);
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            log.error("Failed to publish database schema", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
