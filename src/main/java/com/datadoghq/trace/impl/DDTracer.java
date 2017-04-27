package com.datadoghq.trace.impl;

import com.datadoghq.trace.Sampler;
import com.datadoghq.trace.Writer;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.propagation.Format;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class DDTracer implements io.opentracing.Tracer {


    private Writer writer;
    private final Sampler sampler;

    private final static Logger logger = LoggerFactory.getLogger(DDTracer.class);

    public DDTracer(Writer writer, Sampler sampler) {
        this.writer = writer;
        this.sampler = sampler;
    }

    public DDSpanBuilder buildSpan(String operationName) {
        return new DDSpanBuilder(operationName);
    }

    public <C> void inject(SpanContext spanContext, Format<C> format, C c) {
        throw new UnsupportedOperationException();
    }

    public <C> SpanContext extract(Format<C> format, C c) {
        throw new UnsupportedOperationException();
    }

    public void write(List<Span> trace) {
        this.writer.write(trace);
    }

    public class DDSpanBuilder implements SpanBuilder {

        private final String operationName;
        private Map<String, Object> tags = new HashMap<String, Object>();
        private Long timestamp;
        private DDSpan parent;
        private String serviceName;
        private String resourceName;
        private boolean errorFlag;
        private String spanType;

        public Span start() {

            // build the context
            DDSpanContext context = buildTheSpanContext();

            // FIXME
            logger.debug("Starting new span " + this.operationName);

            return new DDSpan(
                    this.operationName,
                    this.tags,
                    this.timestamp,
                    context);
        }

        public DDTracer.DDSpanBuilder withTag(String tag, Number number) {
            return withTag(tag, (Object) number);
        }

        public DDTracer.DDSpanBuilder withTag(String tag, String string) {
            return withTag(tag, (Object) string);
        }

        public DDTracer.DDSpanBuilder withTag(String tag, boolean bool) {
            return withTag(tag, (Object) bool);
        }

        public DDSpanBuilder(String operationName) {
            this.operationName = operationName;
        }

        public DDTracer.DDSpanBuilder asChildOf(Span span) {
            this.parent = (DDSpan) span;
            return this;
        }

        public DDTracer.DDSpanBuilder withStartTimestamp(long timestampMillis) {
            this.timestamp = timestampMillis;
            return this;
        }

        public DDTracer.DDSpanBuilder withServiceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public DDTracer.DDSpanBuilder withResourceName(String resourceName) {
            this.resourceName = resourceName;
            return this;
        }

        public DDTracer.DDSpanBuilder withErrorFlag() {
            this.errorFlag = true;
            return this;
        }

        public DDTracer.DDSpanBuilder withSpanType(String spanType) {
            this.spanType = spanType;
            return this;
        }

        public Iterable<Map.Entry<String, String>> baggageItems() {
            if (parent == null) {
                return Collections.emptyList();
            }
            return parent.context().baggageItems();
        }

        // UnsupportedOperation
        public DDTracer.DDSpanBuilder asChildOf(SpanContext spanContext) {
            throw new UnsupportedOperationException("Should be a Span instead of a context due to DD implementation");
        }

        public DDTracer.DDSpanBuilder addReference(String referenceType, SpanContext spanContext) {
            throw new UnsupportedOperationException();
        }


        // Private methods
        private DDTracer.DDSpanBuilder withTag(String tag, Object value) {
            this.tags.put(tag, value);
            return this;
        }

        private long generateNewId() {
            return System.nanoTime();
        }


        private DDSpanContext buildTheSpanContext() {

            long generatedId = generateNewId();
            DDSpanContext context;
            DDSpanContext p = this.parent != null ? (DDSpanContext) this.parent.context() : null;

            // some attributes are inherited from the parent
            context = new DDSpanContext(
                    this.parent == null ? generatedId : p.getTraceId(),
                    generatedId,
                    this.parent == null ? 0L : p.getSpanId(),
                    this.parent == null ? this.serviceName : p.getServiceName(),
                    this.resourceName,
                    this.parent == null ? null : p.getBaggageItems(),
                    errorFlag,
                    null,
                    this.spanType,
                    true,
                    this.parent == null ? null : p.getTrace(),
                    DDTracer.this
            );

            logger.debug("Building a new span context." + context.toString());

            return context;
        }


    }


}
