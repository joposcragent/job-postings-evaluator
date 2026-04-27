package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.support

import feign.FeignException
import feign.Request
import feign.Response
import java.nio.charset.StandardCharsets

object FeignTestSupport {
	fun feignError(status: Int, body: String = ""): FeignException {
		val req = Request.create(
			"GET",
			"http://joposcragent.test/remote",
			emptyMap(),
			null,
			StandardCharsets.UTF_8,
		)
		val res = Response.builder()
			.status(status)
			.reason("r")
			.request(req)
			.body(body, StandardCharsets.UTF_8)
			.build()
		return FeignException.errorStatus("test", res)
	}
}
