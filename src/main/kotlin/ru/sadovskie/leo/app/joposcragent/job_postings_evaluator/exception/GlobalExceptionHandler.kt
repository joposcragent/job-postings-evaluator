package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.exception

import feign.FeignException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.server.ResponseStatusException
import java.io.PrintWriter
import java.io.StringWriter

@RestControllerAdvice
class GlobalExceptionHandler {

	private val log = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(ResponseStatusException::class)
	fun responseStatusException(e: ResponseStatusException): ResponseEntity<String> {
		val code = e.statusCode
		val status400 = code.value() == HttpStatus.BAD_REQUEST.value()
		val status404 = code.value() == HttpStatus.NOT_FOUND.value()
		if (status400 || status404) {
			log.warn("responseStatusException status={} reason={}", code.value(), e.reason)
			if (e.reason == null) {
				return ResponseEntity.status(code).build()
			}
			return ResponseEntity.status(code)
				.contentType(MediaType.TEXT_PLAIN)
				.body(e.reason)
		}
		if (code.is4xxClientError) {
			log.warn("responseStatusException status={} reason={} message={}", code.value(), e.reason, e.message)
		} else {
			log.error("responseStatusException status={} reason={}", code.value(), e.reason, e)
		}
		val body = (e.message ?: e.reason) ?: e.toString()
		return ResponseEntity.status(code)
			.contentType(MediaType.TEXT_PLAIN)
			.body(body)
	}

	@ExceptionHandler(
		MethodArgumentNotValidException::class,
		MissingServletRequestParameterException::class,
	)
	fun badRequestException(e: Exception): ResponseEntity<Void> {
		log.warn("badRequest: {}", e.toString())
		return ResponseEntity.badRequest().build()
	}

	@ExceptionHandler(FeignException::class)
	fun feign(e: FeignException): ResponseEntity<String> {
		log.error("feign downstream status={}", e.status(), e)
		return ResponseEntity.status(500)
			.contentType(MediaType.TEXT_PLAIN)
			.body("Downstream: HTTP " + e.status() + (e.contentUTF8()?.let { " " + it } ?: ""))
	}

	@ExceptionHandler(Exception::class)
	fun unhandled(e: Exception): ResponseEntity<String> {
		log.error("unhandled exception", e)
		return ResponseEntity.status(500)
			.contentType(MediaType.TEXT_PLAIN)
			.body(stack(e))
	}
}

private fun stack(e: Throwable) = StringWriter().also { w -> e.printStackTrace(PrintWriter(w)) }.toString()
