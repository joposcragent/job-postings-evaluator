package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.exception

import feign.FeignException
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

	@ExceptionHandler(ResponseStatusException::class)
	fun responseStatusException(e: ResponseStatusException): ResponseEntity<String> {
		val code = e.statusCode
		if (code == HttpStatus.BAD_REQUEST || code == HttpStatus.NOT_FOUND) {
			if (e.reason == null) {
				return ResponseEntity.status(code).build()
			}
			return ResponseEntity.status(code)
				.contentType(MediaType.TEXT_PLAIN)
				.body(e.reason)
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
	fun badRequestException(): ResponseEntity<Void> = ResponseEntity.badRequest().build()

	@ExceptionHandler(FeignException::class)
	fun feign(e: FeignException): ResponseEntity<String> = ResponseEntity.status(500)
		.contentType(MediaType.TEXT_PLAIN)
		.body("Downstream: HTTP " + e.status() + (e.contentUTF8()?.let { " " + it } ?: ""))

	@ExceptionHandler(Exception::class)
	fun unhandled(e: Exception): ResponseEntity<String> = ResponseEntity.status(500)
		.contentType(MediaType.TEXT_PLAIN)
		.body(stack(e))
}

private fun stack(e: Throwable) = StringWriter().also { w -> e.printStackTrace(PrintWriter(w)) }.toString()
