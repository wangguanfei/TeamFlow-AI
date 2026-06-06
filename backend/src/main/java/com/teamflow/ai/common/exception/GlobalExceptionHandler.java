package com.teamflow.ai.common.exception;

import com.teamflow.ai.common.api.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器：把各类异常统一转换为 {@link ApiResult} 错误响应，并按严重程度分级记录日志。
 *
 * <p>日志分级原则（便于线上按级别过滤）：
 * <ul>
 *   <li>{@code WARN}：可预期的业务/客户端错误（业务异常、参数校验失败、越权、上传超限）——
 *       通常是调用方问题，记录请求路径与原因以便复现，但不需要堆栈；</li>
 *   <li>{@code DEBUG}：高频且无害的噪音（404、405、415）——默认不输出，排查时再开；</li>
 *   <li>{@code ERROR}：兜底的未知异常——记录完整堆栈，是需要重点关注的服务端缺陷。</li>
 * </ul>
 * 所有日志都带上 {@code 方法 + 路径}，配合 MDC 中的 traceId 可快速定位单次请求。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResult<Void>> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        HttpStatus status = statusOf(exception.getCode());
        log.warn("业务异常 {} code={} message={}", path(request), exception.getCode(), exception.getMessage());
        return ResponseEntity.status(status).body(ApiResult.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationException(MethodArgumentNotValidException exception, HttpServletRequest request) {
        FieldError fieldError = exception.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "请求参数不合法" : fieldError.getDefaultMessage();
        String field = fieldError == null ? "?" : fieldError.getField();
        log.warn("参数校验失败 {} field={} message={}", path(request), field, message);
        return ResponseEntity.badRequest().body(ApiResult.error(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        log.warn("约束校验失败 {} message={}", path(request), exception.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.error(400, exception.getMessage()));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResult<Void>> handleBadRequest(Exception exception, HttpServletRequest request) {
        log.warn("请求参数解析失败 {} cause={}", path(request), exception.getMessage());
        return ResponseEntity.badRequest().body(ApiResult.error(400, "请求参数不合法"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
        log.debug("资源不存在 {}", path(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResult.error(404, "资源不存在"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        log.debug("请求方法不支持 {} method={}", path(request), exception.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiResult.error(405, "请求方法不支持"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResult<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        log.debug("请求内容类型不支持 {} contentType={}", path(request), exception.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(ApiResult.error(415, "请求内容类型不支持"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResult<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        log.warn("上传文件过大 {} maxBytes={}", path(request), exception.getMaxUploadSize());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResult.error(413, "上传文件过大"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResult<Void>> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        log.warn("访问被拒绝 {} reason={}", path(request), exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResult.error(403, "没有访问权限"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleException(Exception exception, HttpServletRequest request) {
        log.error("未捕获异常 {}", path(request), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.error(500, "系统异常，请联系管理员"));
    }

    /** 拼出「方法 路径」用于日志定位，例如 {@code POST /api/tasks}。 */
    private String path(HttpServletRequest request) {
        if (request == null) {
            return "-";
        }
        return request.getMethod() + " " + request.getRequestURI();
    }

    private HttpStatus statusOf(int code) {
        return switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 413 -> HttpStatus.PAYLOAD_TOO_LARGE;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
