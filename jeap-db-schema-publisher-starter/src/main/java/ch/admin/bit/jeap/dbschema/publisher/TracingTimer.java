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
    private static final String NON_EXCEPTION_FAILURE_MESSAGE = "Synchronous non-exception failure while invoking action";

    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    TracingTimer(Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    CompletableFuture<Void> traceAndTime(String spanName, String timerName, Supplier<CompletableFuture<Void>> action) {
        Span span = (tracer != null) ? tracer.nextSpan().name(spanName).start() : null;
        Timer.Sample sample = (meterRegistry != null) ? Timer.start(meterRegistry) : null;
        CompletableFuture<Void> actionFuture = null;
        Exception synchronousException = null;

        Tracer.SpanInScope spanInScope = (span != null) ? tracer.withSpan(span) : null;
        try (spanInScope) {
            actionFuture = action.get();
            return actionFuture.whenComplete((result, ex) -> safelyRecordCompletion(timerName, sample, span, ex));
        } catch (Exception ex) {
            synchronousException = ex;
            safelyRecordCompletion(timerName, sample, span, ex);
            throw ex;
        } finally {
            if (actionFuture == null && synchronousException == null) {
                safelyRecordCompletion(timerName, sample, span, new Exception(NON_EXCEPTION_FAILURE_MESSAGE));
            }
        }
    }

    private void safelyRecordCompletion(String timerName, Timer.Sample sample, Span span, Throwable original) {
        try {
            recordCompletion(timerName, sample, span, original);
        } catch (Exception cleanupEx) {
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
