package com.sacolao.common.exception;

import com.sacolao.common.api.ApiError;
import com.sacolao.common.api.FieldErrorDetail;
import com.sacolao.tenant.TenantNotBoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(400, "VALIDATION_ERROR", "Dados inválidos", request, errors);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException ex, HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();
        return respond(400, "VALIDATION_ERROR", "Dados inválidos", request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        List<FieldErrorDetail> errors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return respond(400, "VALIDATION_ERROR", "Dados inválidos", request, errors);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(HttpServletRequest request) {
        return respond(400, "BAD_REQUEST", "Requisição inválida", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpServletRequest request) {
        return respond(405, "METHOD_NOT_ALLOWED", "Método não permitido", request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        return respond(403, "FORBIDDEN", "Acesso negado", request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(HttpServletRequest request) {
        return respond(401, "UNAUTHORIZED", "Não autenticado", request, List.of());
    }

    @ExceptionHandler(TenantNotBoundException.class)
    public ResponseEntity<ApiError> handleTenant(HttpServletRequest request) {
        return respond(403, "FORBIDDEN", "Operação requer um estabelecimento", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(HttpServletRequest request) {
        return respond(409, "CONFLICT", "Registro em conflito", request, List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public Object handleNotFound(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return new ModelAndView("forward:/pages/404.html");
        }
        return respond(404, "NOT_FOUND", "Recurso não encontrado", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado em {}", request.getRequestURI(), ex);
        return respond(500, "INTERNAL_ERROR", "Ocorreu um erro interno. Tente novamente.", request, List.of());
    }

    private ResponseEntity<ApiError> respond(
            int status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldErrorDetail> errors
    ) {
        ApiError body = ApiError.of(status, code, message, request.getRequestURI(), errors);
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(body);
    }
}
