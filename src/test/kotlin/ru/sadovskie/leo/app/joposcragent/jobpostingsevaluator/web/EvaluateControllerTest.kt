package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.web

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.dto.JobPostingsUidsEvaluatedItem
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.dto.JobPostingsUidsEvaluatedList
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.service.EvaluateSyncService
import java.util.UUID

class EvaluateControllerTest {

	private val evaluateSyncService: EvaluateSyncService = mock()
	private lateinit var mockMvc: MockMvc

	@BeforeEach
	fun setup() {
		mockMvc = MockMvcBuilders.standaloneSetup(EvaluateController(evaluateSyncService)).build()
	}

	@Test
	fun `evaluate sync returns 200`() {
		val uuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
		whenever(evaluateSyncService.evaluateSync(any())).thenReturn(
			JobPostingsUidsEvaluatedList(
				listOf(JobPostingsUidsEvaluatedItem(uuid, EvaluationStatus.PENDING)),
			),
		)
		mockMvc.perform(
			post("/evaluate/sync")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"list":["00000000-0000-0000-0000-000000000001"]}"""),
		).andExpect(status().isOk)
	}

	@Test
	fun `evaluate async returns 501`() {
		mockMvc.perform(post("/evaluate/async"))
			.andExpect(status().isNotImplemented)
	}

	@Test
	fun `get async status returns 501`() {
		mockMvc.perform(
			get("/evaluate/async/00000000-0000-0000-0000-000000000002"),
		).andExpect(status().isNotImplemented)
	}
}
