package com.fixtrack.common;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tomcat.util.http.InvalidParameterException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fixtrack.infra.security.InvalidTokenException;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;

// Traduce todas las excepciones a respuestas ProblemDetail con el mismo formato y mensajes en español
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String ERRORS = "errors";
	private static final String TIMESTAMP = "timestamp";

	@ExceptionHandler(BusinessException.class)
	public ProblemDetail handleBusiness(BusinessException ex) {
		return problem(ex.getStatus(), ex.getMessage());
	}

	@ExceptionHandler(InvalidTokenException.class)
	public ProblemDetail handleInvalidToken(InvalidTokenException ex) {
		return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	@ExceptionHandler(AuthenticationException.class)
	public ProblemDetail handleAuthentication(AuthenticationException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para acceder a este recurso");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return problem(HttpStatus.FORBIDDEN, "No tiene permiso para realizar esta acción");
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException ex) {
		return problem(HttpStatus.CONFLICT, "Otro usuario modificó este registro; recargue e intente de nuevo");
	}

	// Respaldo de las validaciones del servicio cuando la base de datos rechaza un duplicado
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		log.warn("Restricción de base de datos violada: {}", ex.getMostSpecificCause().getMessage());
		return problem(HttpStatus.CONFLICT, "La operación choca con datos existentes, por ejemplo un valor duplicado");
	}

	// Validaciones de @Validated en servicios u otros componentes
	@ExceptionHandler(ConstraintViolationException.class)
	public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
		List<FieldErrorResponse> errors = ex.getConstraintViolations().stream()
				.map(violation -> new FieldErrorResponse(lastNode(violation.getPropertyPath()), violation.getMessage()))
				.toList();
		return validationProblem(errors);
	}

	// Un sort con un campo inexistente o mal formado (?sort=noExiste) es un error del cliente, no del servidor
	@ExceptionHandler({ PropertyReferenceException.class, InvalidDataAccessApiUsageException.class })
	public ProblemDetail handleInvalidQuery(RuntimeException ex) {
		log.warn("Consulta rechazada: {}", ex.getMessage());
		return problem(HttpStatus.BAD_REQUEST, "El orden o los filtros pedidos no son válidos");
	}

	// Tomcat no pudo decodificar un parámetro (no venía en UTF-8): es un error del cliente, no del servidor
	@ExceptionHandler(InvalidParameterException.class)
	public ProblemDetail handleInvalidEncoding(InvalidParameterException ex) {
		log.warn("Parámetro con codificación inválida: {}", ex.getMessage());
		return problem(HttpStatus.BAD_REQUEST, "La petición tiene caracteres no válidos; revise lo que escribió e intente de nuevo");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		log.error("Error no controlado", ex);
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado; intente de nuevo más tarde");
	}

	// Errores de @Valid en el cuerpo de la petición, campo por campo
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
				.toList();
		return ResponseEntity.badRequest().body(validationProblem(errors));
	}

	// Errores de validación en @PathVariable y @RequestParam
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldErrorResponse> errors = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> new FieldErrorResponse(result.getMethodParameter().getParameterName(),
								error.getDefaultMessage())))
				.toList();
		return ResponseEntity.badRequest().body(validationProblem(errors));
	}

	// Parámetros de la URL con un tipo inválido (por ejemplo, un estado que no existe); los enums listan sus valores
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		Class<?> type = ex.getRequiredType();
		String message = type != null && type.isEnum()
				? "no es un valor válido; use " + Arrays.stream(type.getEnumConstants()).map(String::valueOf)
						.collect(Collectors.joining(", "))
				: "tiene un formato inválido";
		String field = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName()
				: ex.getPropertyName();
		return ResponseEntity.badRequest().body(validationProblem(List.of(new FieldErrorResponse(field, message))));
	}

	// Parámetro obligatorio de la URL que no llegó (por ejemplo, la fecha de un informe)
	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return ResponseEntity.badRequest().body(validationProblem(
				List.of(new FieldErrorResponse(ex.getParameterName(), "es obligatorio"))));
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return ResponseEntity.badRequest()
				.body(problem(HttpStatus.BAD_REQUEST, "El cuerpo de la petición está mal formado o tiene valores inválidos"));
	}

	// Pone título en español y fecha a los errores de Spring MVC (404 de ruta, 405, 415...)
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail detail) {
			detail.setTitle(titleFor(statusCode));
			detail.setProperty(TIMESTAMP, OffsetDateTime.now());
		}
		return response;
	}

	private ProblemDetail validationProblem(List<FieldErrorResponse> errors) {
		ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Hay datos inválidos en la petición");
		detail.setProperty(ERRORS, errors);
		return detail;
	}

	private ProblemDetail problem(HttpStatusCode status, String message) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
		detail.setTitle(titleFor(status));
		detail.setProperty(TIMESTAMP, OffsetDateTime.now());
		return detail;
	}

	private String titleFor(HttpStatusCode status) {
		return switch (status.value()) {
			case 400 -> "Solicitud inválida";
			case 401 -> "No autenticado";
			case 403 -> "Acceso denegado";
			case 404 -> "No encontrado";
			case 405 -> "Método no permitido";
			case 409 -> "Conflicto";
			case 415 -> "Tipo de contenido no soportado";
			case 429 -> "Demasiados intentos";
			case 500 -> "Error interno";
			default -> status instanceof HttpStatus known ? known.getReasonPhrase() : "Error";
		};
	}

	private String lastNode(Path path) {
		String name = null;
		for (Path.Node node : path) {
			name = node.getName();
		}
		return name;
	}
}
