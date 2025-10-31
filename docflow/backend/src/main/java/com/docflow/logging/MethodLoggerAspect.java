package com.docflow.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
public class MethodLoggerAspect {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MethodLoggerAspect.class);

    @Around("execution(* com.docflow..*(..)) && " +
        "!within(com.docflow.logging.MethodLoggerAspect) && " +
        "!within(org.springframework.web.filter.GenericFilterBean+)")
    public Object logMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().toShortString();
        Object[] args = joinPoint.getArgs();
        log.debug("▶️ Entering {} with args: {}", method, Arrays.toString(args));
        long start = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            log.debug("✅ Exiting {} -> {} ({} ms)", method, result, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            log.error("💥 Exception in {}: {}", method, ex.getMessage(), ex);
            throw ex;
        }
    }
}

