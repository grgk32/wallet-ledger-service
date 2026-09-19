package com.example.walletledger.adapters.web;

import com.example.walletledger.adapters.web.dto.ApiError;
import com.example.walletledger.adapters.web.dto.ErrorCode;
import com.example.walletledger.core.exception.LedgerDomainException;
import com.example.walletledger.usecases.exception.AccountNotFoundException;
import com.example.walletledger.usecases.exception.IdempotencyKeyReusedException;
import com.example.walletledger.usecases.exception.TransferInProgressException;
import com.example.walletledger.usecases.exception.TransferRejectedException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_MESSAGE = "the request failed validation";
    private static final String INTERNAL_MESSAGE = "the request could not be processed";
    private static final long MINIMUM_RETRY_AFTER_SECONDS = 1L;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onInvalidRequestBody(MethodArgumentNotValidException invalidBody) {
        var details = invalidBody.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describeFieldError)
                .toList();
        return logAndBuild(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, details,
                invalidBody);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onInvalidMethodArgument(HandlerMethodValidationException invalidArgument) {
        var details = invalidArgument.getAllValidationResults().stream()
                .flatMap(validationResult -> validationResult.getResolvableErrors().stream())
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .toList();
        return logAndBuild(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, VALIDATION_MESSAGE, details,
                invalidArgument);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onMissingHeader(MissingRequestHeaderException missingHeader) {
        return logAndBuild(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "required header " + missingHeader.getHeaderName() + " is absent", List.of(), missingHeader);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onUnreadableBody(HttpMessageNotReadableException unreadableBody) {
        return logAndBuild(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "the request body could not be parsed", List.of(), unreadableBody);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError onTypeMismatch(MethodArgumentTypeMismatchException typeMismatch) {
        return logAndBuild(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "parameter " + typeMismatch.getName() + " has the wrong type", List.of(), typeMismatch);
    }

    @ExceptionHandler(LedgerDomainException.class)
    public ApiError onDomainRejection(LedgerDomainException domainRejection, HttpServletResponse response) {
        var translation = RejectionReasonTranslator.translate(domainRejection.rejectionReason());
        response.setStatus(translation.status().value());
        return logAndBuild(translation.status(), translation.errorCode(), domainRejection.getMessage(), List.of(),
                domainRejection);
    }

    @ExceptionHandler(TransferRejectedException.class)
    public ApiError onTransferRejected(TransferRejectedException rejection, HttpServletResponse response) {
        var translation = RejectionReasonTranslator.translate(rejection.reason());
        response.setStatus(translation.status().value());
        return logAndBuild(translation.status(), translation.errorCode(), rejection.getMessage(), List.of(),
                rejection);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError onAccountNotFound(AccountNotFoundException accountNotFound) {
        return logAndBuild(HttpStatus.NOT_FOUND, ErrorCode.ACCOUNT_NOT_FOUND, accountNotFound.getMessage(),
                List.of(), accountNotFound);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError onIdempotencyKeyReused(IdempotencyKeyReusedException keyReuse) {
        return logAndBuild(HttpStatus.CONFLICT, ErrorCode.IDEMPOTENCY_KEY_REUSED, keyReuse.getMessage(),
                List.of(), keyReuse);
    }

    @ExceptionHandler(TransferInProgressException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError onTransferInProgress(TransferInProgressException stillRunning, HttpServletResponse response) {
        var retryAfterSeconds = Math.max(stillRunning.waitBudget().toSeconds(), MINIMUM_RETRY_AFTER_SECONDS);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        return logAndBuild(HttpStatus.CONFLICT, ErrorCode.TRANSFER_IN_PROGRESS, stillRunning.getMessage(),
                List.of(), stillRunning);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError onUnknownResource(NoResourceFoundException unknownResource) {
        return logAndBuild(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "no resource is mapped to the requested path", List.of(), unknownResource);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiError onUnsupportedMethod(HttpRequestMethodNotSupportedException unsupportedMethod) {
        return logAndBuild(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
                "method " + unsupportedMethod.getMethod() + " is not supported by this resource", List.of(),
                unsupportedMethod);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiError onUnsupportedMediaType(HttpMediaTypeNotSupportedException unsupportedMediaType) {
        return logAndBuild(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "the request media type is not supported", List.of(), unsupportedMediaType);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError onUnexpectedFailure(Exception unexpectedFailure) {
        return logAndBuild(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, INTERNAL_MESSAGE,
                List.of(), unexpectedFailure);
    }

    private ApiError logAndBuild(HttpStatus status,
                                 ErrorCode errorCode,
                                 String message,
                                 List<String> details,
                                 Exception cause) {
        var correlationId = CorrelationId.current();
        if (status.is5xxServerError()) {
            log.error("correlationId={} status={} errorCode={}", correlationId, status.value(), errorCode, cause);
        } else {
            log.warn("correlationId={} status={} errorCode={} message={}", correlationId, status.value(), errorCode,
                    cause.getMessage());
        }
        return new ApiError(errorCode, message, details, correlationId, Instant.now());
    }

    private static String describeFieldError(FieldError fieldError) {
        return fieldError.getField() + " " + fieldError.getDefaultMessage();
    }
}
