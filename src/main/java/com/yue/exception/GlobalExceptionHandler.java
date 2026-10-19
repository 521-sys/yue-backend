package com.yue.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理：把业务异常与参数校验异常转成统一 JSON 错误响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(com.yue.email.EmailRateLimitException.class)
    public ResponseEntity<Map<String, Object>> emailRateLimit(com.yue.email.EmailRateLimitException e) {
        return ResponseEntity.status(429).body(Map.of(
                "error", "请求过于频繁，请稍后重试",
                "retryAfterSeconds", e.retryAfterSeconds()));
    }

    @ExceptionHandler({com.yue.email.EmailUnavailableException.class,
            com.yue.email.EmailDeliveryException.class})
    public ResponseEntity<Map<String, String>> emailUnavailable(RuntimeException e) {
        return ResponseEntity.status(503).body(Map.of("error", "邮箱服务暂不可用，请稍后重试"));
    }

    @ExceptionHandler(com.yue.sms.SmsRateLimitException.class)
    public ResponseEntity<Map<String, Object>> smsRateLimit(com.yue.sms.SmsRateLimitException e) {
        return ResponseEntity.status(429).body(Map.of(
                "error", "请求过于频繁，请稍后重试",
                "retryAfterSeconds", e.retryAfterSeconds()));
    }

    @ExceptionHandler({com.yue.sms.SmsUnavailableException.class, com.yue.sms.SmsDeliveryException.class})
    public ResponseEntity<Map<String, String>> smsUnavailable(RuntimeException e) {
        return ResponseEntity.status(503).body(Map.of("error", "短信服务暂不可用，请稍后重试"));
    }
    @ExceptionHandler(com.yue.admin.AdminApiException.class)
    public ResponseEntity<Map<String,String>> admin(com.yue.admin.AdminApiException e) {
        return ResponseEntity.status(e.status).body(Map.of("error",e.getMessage()));
    }
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class, org.springframework.dao.DataIntegrityViolationException.class})
    public ResponseEntity<Map<String,String>> conflict(Exception e) {
        return ResponseEntity.status(409).body(Map.of("error","内容已变更或编码冲突，请刷新后重试"));
    }
    @ExceptionHandler({org.springframework.web.bind.MissingServletRequestParameterException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String,String>> invalid(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error","请求参数格式不正确"));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String,String>> large(Exception e) {
        return ResponseEntity.status(413).body(Map.of("error","上传文件超过大小限制"));
    }

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常（如用户名已存在、密码错误）→ 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> illegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** @Valid 参数校验失败 → 400，附带字段错误明细 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        if (msg.isEmpty()) {
            msg = "参数校验失败";
        }
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    /** 兜底：未预期异常 → 500，不泄漏堆栈（堆栈记录到服务日志便于排查） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("未预期异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "服务器内部错误"));
    }

    private String formatFieldError(FieldError f) {
        return f.getField() + ": " + f.getDefaultMessage();
    }
}
