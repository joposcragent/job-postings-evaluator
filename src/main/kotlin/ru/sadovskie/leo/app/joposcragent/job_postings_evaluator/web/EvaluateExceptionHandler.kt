package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.EvaluationSettingsException
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service.NoPostingsToEvaluateException

@RestControllerAdvice(assignableTypes = [EvaluateController::class])
class EvaluateExceptionHandler {

	private val log = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(NoPostingsToEvaluateException::class)
	fun notFound(): ResponseEntity<Void> = ResponseEntity.notFound().build()

	@ExceptionHandler(MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
	fun badRequest(ex: Exception): ResponseEntity<Void> {
		log.debug("Bad request: {}", ex.message)
		return ResponseEntity.badRequest().build()
	}

	@ExceptionHandler(EvaluationSettingsException::class)
	fun settingsError(ex: EvaluationSettingsException): ResponseEntity<String> {
		log.warn("Evaluate settings error: {}", ex.message)
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.contentType(MediaType.TEXT_PLAIN)
			.body(ex.stackTraceToString())
	}

	@ExceptionHandler(Exception::class)
	fun uncaught(ex: Exception): ResponseEntity<String> {
		log.error("Evaluate request failed", ex)
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.contentType(MediaType.TEXT_PLAIN)
			.body(ex.stackTraceToString())
	}
}
