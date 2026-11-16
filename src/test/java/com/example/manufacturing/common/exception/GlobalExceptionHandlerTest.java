package com.example.manufacturing.common.exception;

import com.example.manufacturing.plant.Plant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.ProblemDetail;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct tests for handlers that are hard to trigger through MockMvc.
 * Validation, 404, 409, 422 and type-mismatch responses are also covered end-to-end in the controller tests.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void optimisticLock_returns409() {
        ProblemDetail problem = handler.handleOptimisticLock(new OptimisticLockingFailureException("stale"));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Concurrent update");
    }

    @Test
    void dataIntegrity_returns409WithoutLeakingSql() {
        ProblemDetail problem = handler.handleDataIntegrity(
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"plants_code_key\""));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).doesNotContain("plants_code_key");
    }

    @Test
    void unknownSortProperty_returns400() {
        ProblemDetail problem = handler.handleBadSortProperty(
                new PropertyReferenceException("bogus", TypeInformation.of(Plant.class), List.of()));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Cannot sort by 'bogus'");
    }

    @Test
    void unexpectedError_returns500WithoutLeakingMessage() {
        ProblemDetail problem = handler.handleUnexpected(new IllegalStateException("secret internals"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("secret internals");
    }
}
