package ch.admin.bit.jeap.dbschema.publisher;

import brave.Span;
import brave.Tracer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Utility class for tracing and timing operations using Brave and Micrometer.
 * It provides a method to execute an action while tracing it with a span and timing it with
 * a timer. The timer records the duration of the action and its success or failure status.
 * This class is designed to be used in asynchronous contexts, and tracing/metrics are fully
 * optional, allowing for graceful degradation if the tracer or meter registry is not available.
 */
class TracingTimer {

    private static final String TAG_STATUS = "status";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_ERROR = "error";

    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    TracingTimer(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    CompletableFuture<Void> traceAndTime(String spanName, String timerName, Supplier<CompletableFuture<Void>> action) {
        Span span = (tracer != null) ? tracer.nextSpan().name(spanName).start() : null;
        Timer.Sample sample = (meterRegistry != null) ? Timer.start(meterRegistry) : null;

        try (Tracer.SpanInScope ignored = (span != null) ? tracer.withSpanInScope(span) : null) {
            return action.get().whenComplete((result, ex) -> stopTimer(timerName, sample, ex));
        } finally {
            if (span != null) {
                span.finish();
            }
        }
    }

    private void stopTimer(String timerName, Timer.Sample sample, Throwable ex) {
        if (sample != null) {
            String status = (ex != null) ? STATUS_ERROR : STATUS_SUCCESS;
            sample.stop(meterRegistry.timer(timerName, TAG_STATUS, status));
        }
    }
}
