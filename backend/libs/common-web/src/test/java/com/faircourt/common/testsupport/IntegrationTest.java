package com.faircourt.common.testsupport;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * PostgreSQL-backed integration test of one service against the {@code faircourt_test} database (conventions: tests).
 * Flyway clean is allowed only here; call {@link TestDatabase#reset} before each test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(properties = "spring.flyway.clean-disabled=false")
public @interface IntegrationTest {}
