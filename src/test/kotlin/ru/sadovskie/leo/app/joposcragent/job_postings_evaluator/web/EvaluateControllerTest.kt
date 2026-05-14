package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.openapi.model.UuidsList
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.exception.GlobalExceptionHandler
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service.AsyncEvaluationRunner
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service.EvaluationService
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class EvaluateControllerTest {

	@Mock
	private lateinit var evaluation: EvaluationService

	@Mock
	private lateinit var async: AsyncEvaluationRunner

	private lateinit var mvc: MockMvc

	@BeforeEach
	fun setUp() {
		mvc = MockMvcBuilders
			.standaloneSetup(EvaluateController(evaluation, async))
			.setControllerAdvice(GlobalExceptionHandler())
			.build()
	}

	@Test
	fun `sync list rejects empty list`() {
		whenever(evaluation.evaluateSyncList(UuidsList(emptyList()))).thenThrow(
			ResponseStatusException(HttpStatus.BAD_REQUEST),
		)
		val result = mvc.perform(
			post("/evaluate/sync/list")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"list\":[]}"),
		).andReturn()
		assertEquals(400, result.response.status, result.response.contentAsString)
		verify(evaluation).evaluateSyncList(UuidsList(emptyList()))
	}

	@Test
	fun `async one triggers background run`() {
		val u = UUID.fromString("00000000-0000-0000-0000-000000000001")
		mvc.perform(
			post("/evaluate/async/$u")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-Joposcragent-correlationId", "00000000-0000-0000-0000-000000000002"),
		).andExpect(status().isOk)
		val cid = UUID.fromString("00000000-0000-0000-0000-000000000002")
		verify(async).runOne(eq(u), eq(cid))
	}
}
