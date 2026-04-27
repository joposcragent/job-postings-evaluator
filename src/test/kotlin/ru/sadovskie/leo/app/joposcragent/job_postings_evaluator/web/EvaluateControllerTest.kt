package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
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
		mvc.perform(
			post("/evaluate/sync/list")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"list\":[]}"),
		).andExpect(status().isBadRequest)
		verify(evaluation, never()).evaluateSyncList(any())
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
