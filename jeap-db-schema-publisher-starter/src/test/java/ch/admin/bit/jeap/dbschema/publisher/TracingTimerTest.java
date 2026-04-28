package ch.admin.bit.jeap.dbschema.publisher;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TracingTimerTest {

    private static final String SPAN_NAME = "publish-db-schema";
    private static final String TIMER_NAME = "jeap-publish-database-schema";

    private Tracer tracer;
    private Span span;
    private Tracer.SpanInScope spanInScope;
    private MeterRegistry meterRegistry;
    private TracingTimer tracingTimer;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        span = mock(Span.class);
        spanInScope = mock(Tracer.SpanInScope.class);
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(SPAN_NAME)).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(spanInScope);

        meterRegistry = new SimpleMeterRegistry();
        tracingTimer = new TracingTimer(tracer, meterRegistry);
    }

    private static MeterRegistry brokenRegistry(String timerName, RuntimeException failure) {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (timerName.equals(id.getName())) {
                    throw failure;
                }
                return id;
            }
        });
        return registry;
    }

    @Test
    void traceAndTime_endsSpanWithoutError_whenFutureCompletesSuccessfully() {
        CompletableFuture<Void> result = tracingTimer.traceAndTime(SPAN_NAME, TIMER_NAME,
                () -> CompletableFuture.completedFuture(null));

        assertThat(result).isCompleted();
        verify(span, never()).error(any());
        verify(span).end();
        assertThat(meterRegistry.find(TIMER_NAME).tag("status", "success").timer().count()).isOne();
    }

    @Test
    void traceAndTime_marksSpanAsErrorBeforeEnding_whenFutureCompletesExceptionally() {
        RuntimeException failure = new RuntimeException("boom");

        CompletableFuture<Void> result = tracingTimer.traceAndTime(SPAN_NAME, TIMER_NAME,
                () -> CompletableFuture.failedFuture(failure));

        assertThat(result).isCompletedExceptionally();
        InOrder order = inOrder(span);
        order.verify(span).error(failure);
        order.verify(span).end();
        assertThat(meterRegistry.find(TIMER_NAME).tag("status", "error").timer().count()).isOne();
    }

    @Test
    void traceAndTime_marksSpanAsErrorBeforeEnding_whenSupplierThrowsSynchronously() {
        RuntimeException failure = new RuntimeException("supplier blew up");

        assertThatThrownBy(() -> tracingTimer.traceAndTime(SPAN_NAME, TIMER_NAME, () -> {
            throw failure;
        }))
                .isSameAs(failure);

        InOrder order = inOrder(span);
        order.verify(span).error(failure);
        order.verify(span).end();
        assertThat(meterRegistry.find(TIMER_NAME).tag("status", "error").timer().count()).isOne();
    }

    @Test
    void traceAndTime_doesNotEndSpan_beforeAsyncFutureCompletes() {
        CompletableFuture<Void> pending = new CompletableFuture<>();

        CompletableFuture<Void> result = tracingTimer.traceAndTime(SPAN_NAME, TIMER_NAME, () -> pending);

        // Future not yet complete: span must still be open, otherwise its duration would not include the async work
        // and a later failure couldn't be recorded on it.
        verify(span, never()).end();
        verify(span, never()).error(any());

        pending.completeExceptionally(new IllegalStateException("late failure"));

        assertThat(result).isCompletedExceptionally();
        InOrder order = inOrder(span);
        order.verify(span).error(any(IllegalStateException.class));
        order.verify(span).end();
    }

    @Test
    void traceAndTime_endsSpan_evenWhenMetricsRecordingThrows() {
        RuntimeException registryFailure = new RuntimeException("registry blew up");
        TracingTimer timer = new TracingTimer(tracer, brokenRegistry(TIMER_NAME, registryFailure));

        assertThatThrownBy(() -> timer.traceAndTime(SPAN_NAME, TIMER_NAME,
                () -> CompletableFuture.completedFuture(null)).join())
                .isInstanceOf(CompletionException.class)
                .hasCause(registryFailure);

        // Even though stopTimer threw, the span must still have been ended — otherwise the started span leaks.
        verify(span).end();
    }

    @Test
    void traceAndTime_preservesOriginalSyncFailure_andAddsMetricsFailureAsSuppressed() {
        RuntimeException supplierFailure = new RuntimeException("supplier blew up");
        RuntimeException registryFailure = new RuntimeException("registry blew up");
        TracingTimer timer = new TracingTimer(tracer, brokenRegistry(TIMER_NAME, registryFailure));

        assertThatThrownBy(() -> timer.traceAndTime(SPAN_NAME, TIMER_NAME, () -> {
            throw supplierFailure;
        }))
                .as("Original operation failure must surface to the caller; a metrics-recording failure piggy-backs " +
                        "as a suppressed exception so it remains visible without masking the real cause.")
                .isSameAs(supplierFailure)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).contains(registryFailure));
        verify(span).error(supplierFailure);
        verify(span).end();
    }

    @Test
    void traceAndTime_preservesOriginalAsyncFailure_andAddsMetricsFailureAsSuppressed() {
        RuntimeException futureFailure = new RuntimeException("future blew up");
        RuntimeException registryFailure = new RuntimeException("registry blew up");
        TracingTimer timer = new TracingTimer(tracer, brokenRegistry(TIMER_NAME, registryFailure));

        CompletableFuture<Void> result = timer.traceAndTime(SPAN_NAME, TIMER_NAME,
                () -> CompletableFuture.failedFuture(futureFailure));

        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCause(futureFailure)
                .satisfies(thrown -> assertThat(thrown.getCause().getSuppressed()).contains(registryFailure));
        verify(span).error(futureFailure);
        verify(span).end();
    }

    @Test
    void traceAndTime_cleansUpSpan_whenSupplierThrowsError() {
        Error supplierFailure = new AssertionError("error from supplier");

        assertThatThrownBy(() -> tracingTimer.traceAndTime(SPAN_NAME, TIMER_NAME, () -> {
            throw supplierFailure;
        }))
                .as("Errors must propagate without leaking the started span and timer sample.")
                .isSameAs(supplierFailure);

        InOrder order = inOrder(span);
        order.verify(span).error(supplierFailure);
        order.verify(span).end();
        assertThat(meterRegistry.find(TIMER_NAME).tag("status", "error").timer().count()).isOne();
    }

    @Test
    void traceAndTime_returnsCompletedFutureAndDoesNotCrash_whenTracerAndRegistryAreNull() throws ExecutionException, InterruptedException {
        TracingTimer noOpTimer = new TracingTimer(null, null);

        CompletableFuture<Void> result = noOpTimer.traceAndTime(SPAN_NAME, TIMER_NAME,
                () -> CompletableFuture.completedFuture(null));

        assertThat(result).isCompleted();
        result.get();
    }

    @Test
    void traceAndTime_propagatesFailure_whenTracerAndRegistryAreNull() {
        TracingTimer noOpTimer = new TracingTimer(null, null);
        RuntimeException failure = new RuntimeException("boom");

        CompletableFuture<Void> result = noOpTimer.traceAndTime(SPAN_NAME, TIMER_NAME,
                () -> CompletableFuture.failedFuture(failure));

        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCause(failure);
    }
}
