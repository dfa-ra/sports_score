package com.studentleague.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Pattern MATCH_BY_ID = Pattern.compile("^/api/v1/matches/[^/]+/?$");

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(error(ex.getCode(), ex.getMessage(), List.of(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new ApiError.FieldErrorDetail(err.getField(), err.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", "Проверьте введённые данные", details, request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiError.FieldErrorDetail> details = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", "Проверьте введённые данные", details, request));
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<ApiError> handleAuth(RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error("UNAUTHORIZED", "Нужно войти", List.of(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("FORBIDDEN", "Недостаточно прав", List.of(), request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(error("METHOD_NOT_ALLOWED", "Метод не поддерживается", List.of(), request));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMedia(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(error("UNSUPPORTED_MEDIA_TYPE", "Нужен запрос в формате JSON", List.of(), request));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public void handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        // The client asked for a type Jackson cannot write. A ResponseEntity would
        // be negotiated again and fall through to the generic 500.
        response.setStatus(HttpStatus.NOT_ACCEPTABLE.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getOutputStream(),
                error("NOT_ACCEPTABLE", "Этот формат ответа не поддерживается", List.of(), request)
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(error(
                "BAD_REQUEST",
                "Не хватает части запроса",
                List.of(new ApiError.FieldErrorDetail(ex.getRequestPartName(), "Обязательная часть")),
                request
        ));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiError> handleBinding(ServletRequestBindingException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(error("BAD_REQUEST", "Некорректный запрос", List.of(), request));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException ex, HttpServletRequest request) {
        List<ApiError.FieldErrorDetail> details = ex.getFieldErrors().stream()
                .map(err -> new ApiError.FieldErrorDetail(err.getField(), err.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR", "Проверьте введённые данные", details, request));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(
            HandlerMethodValidationException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_ERROR", "Проверьте введённые данные", List.of(), request));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(error("PAYLOAD_TOO_LARGE", "Файл больше 8 МБ. Нужны JPEG, PNG, WebP или GIF.", List.of(), request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String name = ex.getName() == null ? "id" : ex.getName();
        if (ex.getRequiredType() != null && ex.getRequiredType().isEnum()) {
            return ResponseEntity.badRequest().body(error(
                    "BAD_REQUEST",
                    "Некорректное значение",
                    List.of(new ApiError.FieldErrorDetail(name, "Некорректное значение")),
                    request
            ));
        }
        // QA looks up /api/v1/matches/999999. A bad token on that exact match URL is a missing match.
        // Other routes keep the shared «Некорректный идентификатор» response.
        if (UUID.class.equals(ex.getRequiredType()) && isMatchById(request)) {
            return matchNotFound(request);
        }
        return ResponseEntity.badRequest().body(error(
                "BAD_REQUEST",
                "Некорректный идентификатор",
                List.of(new ApiError.FieldErrorDetail(name, "Ожидается корректный идентификатор")),
                request
        ));
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleUnknownProperty(PropertyReferenceException ex, HttpServletRequest request) {
        return unknownValue(request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String message = "Некорректное тело запроса";
        if (ex.getMostSpecificCause() instanceof InvalidFormatException invalid
                && invalid.getTargetType() != null
                && invalid.getTargetType().isEnum()) {
            message = "Некорректное значение";
        }
        return ResponseEntity.badRequest().body(error("BAD_REQUEST", message, List.of(), request));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(error(
                "BAD_REQUEST",
                "Не хватает параметра",
                List.of(new ApiError.FieldErrorDetail(ex.getParameterName(), "Обязательный параметр")),
                request
        ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleMissingResource(NoResourceFoundException ex, HttpServletRequest request) {
        String message = request.getRequestURI() != null && request.getRequestURI().startsWith("/api/")
                ? "Адрес не найден"
                : "Файл не найден";
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("NOT_FOUND", message, List.of(), request));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("NOT_FOUND", "Адрес не найден", List.of(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        if (causedByUnknownProperty(ex)) {
            return unknownValue(request);
        }
        log.error("Unhandled {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", "Внутренняя ошибка сервера", List.of(), request));
    }

    private ResponseEntity<ApiError> unknownValue(HttpServletRequest request) {
        List<ApiError.FieldErrorDetail> details = request.getParameter("sort") == null
                ? List.of()
                : List.of(new ApiError.FieldErrorDetail("sort", "Некорректное значение"));
        return ResponseEntity.badRequest().body(error("BAD_REQUEST", "Некорректное значение", details, request));
    }

    private static ResponseEntity<ApiError> matchNotFound(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("NOT_FOUND", "Матч не найден", List.of(), request));
    }

    private static boolean isMatchById(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length())
                : uri;
        return MATCH_BY_ID.matcher(path).matches();
    }

    private static boolean causedByUnknownProperty(Throwable ex) {
        Throwable current = ex;
        int guard = 0;
        while (current != null && guard++ < 8) {
            if (current instanceof PropertyReferenceException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ApiError error(String code, String message, List<ApiError.FieldErrorDetail> details, HttpServletRequest request) {
        return new ApiError(code, message, details, Instant.now(), request.getRequestURI());
    }
}
