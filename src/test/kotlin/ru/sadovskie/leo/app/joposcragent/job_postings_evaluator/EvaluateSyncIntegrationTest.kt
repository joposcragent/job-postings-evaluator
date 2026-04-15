package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.reset
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.junit.jupiter.Testcontainers
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.EvaluationSettings
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.EvaluationSettingsException
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.SentenceTransformerHttpClient
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.http.SettingsHttpClient
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, EvaluateTestHttpClientsConfiguration::class)
class EvaluateSyncIntegrationTest @Autowired constructor(
	private val webApplicationContext: WebApplicationContext,
	private val jdbcTemplate: JdbcTemplate,
	private val settingsHttpClient: SettingsHttpClient,
	private val sentenceTransformerHttpClient: SentenceTransformerHttpClient,
) {

	private lateinit var mockMvc: MockMvc

	private val postingUuid: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

	@BeforeEach
	fun setup() {
		reset(settingsHttpClient, sentenceTransformerHttpClient)
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build()
		jdbcTemplate.update("DELETE FROM job_postings.postings")
	}

	@Test
	fun `sync list returns 200 and RELEVANT when similarity above threshold`() {
		insertPosting(postingUuid, "NEW", "hello job")
		whenever(settingsHttpClient.loadForSync()).thenReturn(
			EvaluationSettings(threshold = 0.5, referenceVector = listOf(0.1, 0.2)),
		)
		whenever(sentenceTransformerHttpClient.vectorize(any())).thenReturn(listOf(1.0, 0.0))
		whenever(sentenceTransformerHttpClient.cosineSimilarity(any(), any())).thenReturn(0.91)

		mockMvc.perform(
			post("/evaluate/sync/list")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"list":["$postingUuid"]}"""),
		)
			.andExpect(status().isOk)
			.andExpect(jsonPath("$[0].uuid").value(postingUuid.toString()))
			.andExpect(jsonPath("$[0].status").value("RELEVANT"))

		val status = jdbcTemplate.queryForObject(
			"SELECT evaluation_status::text FROM job_postings.postings WHERE uuid = ?::uuid",
			String::class.java,
			postingUuid.toString(),
		)
		assertEquals("RELEVANT", status)
	}

	@Test
	fun `sync list returns 404 when no evaluatable postings`() {
		insertPosting(postingUuid, "RELEVANT", "x")
		whenever(settingsHttpClient.loadForSync()).thenReturn(
			EvaluationSettings(threshold = 0.5, referenceVector = listOf(0.1)),
		)

		mockMvc.perform(
			post("/evaluate/sync/list")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"list":["$postingUuid"]}"""),
		)
			.andExpect(status().isNotFound)
	}

	@Test
	fun `sync list returns 500 text plain when settings missing`() {
		insertPosting(postingUuid, "NEW", "x")
		whenever(settingsHttpClient.loadForSync()).thenThrow(
			EvaluationSettingsException("Reference context vector is not configured"),
		)

		mockMvc.perform(
			post("/evaluate/sync/list")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""{"list":["$postingUuid"]}"""),
		)
			.andExpect(status().isInternalServerError)
			.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
	}

	private fun insertPosting(uuid: UUID, evaluationStatus: String, content: String) {
		jdbcTemplate.update(
			"""
			INSERT INTO job_postings.postings (uuid, uid, title, url, publication_date, evaluation_status, content)
			VALUES (?::uuid, ?, ?, ?, ?, ?::job_postings.evaluation_status, ?)
			""".trimIndent(),
			uuid.toString(),
			"uid-$uuid",
			"title",
			"https://example.com/j",
			"2026-01-01",
			evaluationStatus,
			content,
		)
	}
}
