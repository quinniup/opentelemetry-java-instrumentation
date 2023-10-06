/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.redisson.v3_17.RedissonSingletons.instrumenter;
import static java.util.logging.Level.WARNING;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.redisson.EndOperationListener;
import io.opentelemetry.javaagent.instrumentation.redisson.PromiseWrapper;
import io.opentelemetry.javaagent.instrumentation.redisson.RedissonRequest;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import io.opentelemetry.javaagent.tooling.AgentInstaller;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.redisson.client.RedisConnection;

public class RedisConnectionInstrumentation implements TypeInstrumentation {
  // debug logger
  private static final Logger logger = Logger.getLogger(RedisConnectionInstrumentation.class.getName());
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.redisson.client.RedisConnection");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("send")), this.getClass().getName() + "$SendAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This RedisConnection connection,
        @Advice.Argument(0) Object arg,
        @Advice.Local("otelRequest") RedissonRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      InetSocketAddress remoteAddress = (InetSocketAddress) connection.getChannel().remoteAddress();
      request = RedissonRequest.create(remoteAddress, arg);
      ObjectMapper om = new ObjectMapper();
      try {
        logger.log(WARNING, "debug redisson :: {0}", om.writeValueAsString(request));
      } catch (JsonProcessingException e) {

      }
      PromiseWrapper<?> promise = request.getPromiseWrapper();
      if (promise == null) {
        return;
      }
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();

      promise.setEndOperationListener(new EndOperationListener<>(instrumenter(), context, request));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelRequest") RedissonRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable != null) {
        instrumenter().end(context, request, null, throwable);
      }
      // span ended in EndOperationListener
    }
  }
}
