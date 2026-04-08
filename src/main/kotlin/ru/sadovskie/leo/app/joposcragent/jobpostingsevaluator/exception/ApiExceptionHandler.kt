package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.exception

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@Order
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(ResponseStatusException::class)
	fun handleResponseStatus(e: ResponseStatusException): ResponseEntity<String> {
		val body = e.reason ?: e.message ?: e.statusCode.toString()
		return ResponseEntity.status(e.statusCode)
			.contentType(MediaType.TEXT_PLAIN)
			.body(body)
	}

	@ExceptionHandler(Exception::class)
	fun handleUncaught(e: Exception): ResponseEntity<String> {
		val msg = e.message ?: e.toString()
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.contentType(MediaType.TEXT_PLAIN)
			.body(msg)
	}
}
