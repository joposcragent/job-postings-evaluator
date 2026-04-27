package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestMdcAndHttpSummaryFilter : OncePerRequestFilter() {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun shouldNotFilter(request: HttpServletRequest): Boolean {
		val path = request.requestURI ?: ""
		return path.startsWith("/actuator")
	}

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val requestId = UUID.randomUUID().toString()
		MDC.put(REQUEST_ID_MDC, requestId)
		val rawCorrelation = request.getHeader(CORRELATION_HEADER)
		if (!rawCorrelation.isNullOrBlank()) {
			runCatching { UUID.fromString(rawCorrelation.trim()) }
				.onSuccess { MDC.put(CORRELATION_ID_MDC, it.toString()) }
		}
		val startNs = System.nanoTime()
		try {
			filterChain.doFilter(request, response)
		} finally {
			val durationMs = (System.nanoTime() - startNs) / 1_000_000L
			val path = request.requestURI?.substringBefore('?') ?: request.requestURI
			log.info(
				"http method={} path={} status={} durationMs={}",
				request.method,
				path,
				response.status,
				durationMs,
			)
			MDC.clear()
		}
	}

	companion object {
		const val CORRELATION_HEADER: String = "X-Joposcragent-correlationId"
		const val CORRELATION_ID_MDC: String = "correlationId"
		const val REQUEST_ID_MDC: String = "requestId"
	}
}
