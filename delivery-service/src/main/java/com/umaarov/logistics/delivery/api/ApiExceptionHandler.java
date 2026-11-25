package com.umaarov.logistics.delivery.api;

import com.umaarov.logistics.delivery.domain.CourierNotFoundException;
import com.umaarov.logistics.delivery.domain.DeliveryNotFoundException;
import com.umaarov.logistics.delivery.domain.DuplicateCourierException;
import com.umaarov.logistics.delivery.domain.IllegalDeliveryTransitionException;
import com.umaarov.logistics.delivery.domain.NoCourierAvailableException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({CourierNotFoundException.class, DeliveryNotFoundException.class})
    ProblemDetail notFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({
            DuplicateCourierException.class,
            IllegalDeliveryTransitionException.class,
            NoCourierAvailableException.class
    })
    ProblemDetail conflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail concurrentModification() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicts with a concurrent change; retry it");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        List<String> errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    String field = error instanceof FieldError fieldError
                            ? fieldError.getField()
                            : error.getObjectName();
                    return field + ": " + error.getDefaultMessage();
                })
                .sorted()
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }
}
