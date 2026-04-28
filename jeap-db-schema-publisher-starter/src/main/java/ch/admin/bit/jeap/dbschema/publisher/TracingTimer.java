package ch.admin.bit.jeap.dbschema.publisher;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Utility class for tracing and timing operations using Micrometer Tracing and Micrometer.
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

        try (Tracer.SpanInScope ignored = (span != null) ? tracer.withSpan(span) : null) {
            return action.get().whenComplete((result, ex) -> safelyRecordCompletion(timerName, sample, span, ex));
        } catch (Throwable ex) {
            // action.get() threw synchronously instead of returning a failed future — record on the timer and span
            // before propagating, otherwise the started span and sample would leak. Catch Throwable so Errors are
            // cleaned up too; only unchecked exceptions can escape the try block, so the rethrow is a precise
            // rethrow and does not require a `throws` declaration.
            safelyRecordCompletion(timerName, sample, span, ex);
            throw ex;
        }
    }

    private void safelyRecordCompletion(String timerName, Timer.Sample sample, Span span, Throwable original) {
        try {
            recordCompletion(timerName, sample, span, original);
        } catch (RuntimeException | Error cleanupEx) {
            // A failure in metrics/tracing recording must never mask the original operation failure. Attach it as
            // suppressed so it remains visible. If there is no original failure (success path), propagate the
            // recording failure so the caller learns about it.
            if (original != null) {
                original.addSuppressed(cleanupEx);
            } else {
                throw cleanupEx;
            }
        }
    }

    private void recordCompletion(String timerName, Timer.Sample sample, Span span, Throwable ex) {
        // Span lifecycle must complete even if metrics recording throws, otherwise the started span leaks.
        try {
            stopTimer(timerName, sample, ex);
        } finally {
            endSpan(span, ex);
        }
    }

    private void stopTimer(String timerName, Timer.Sample sample, Throwable ex) {
        if (sample != null) {
            String status = (ex != null) ? STATUS_ERROR : STATUS_SUCCESS;
            sample.stop(meterRegistry.timer(timerName, TAG_STATUS, status));
        }
    }

    private static void endSpan(Span span, Throwable ex) {
        if (span == null) {
            return;
        }
        if (ex != null) {
            span.error(ex);
        }
        span.end();
    }
}
