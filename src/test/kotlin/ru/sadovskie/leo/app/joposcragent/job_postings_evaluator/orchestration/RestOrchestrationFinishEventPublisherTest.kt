package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.orchestration

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.web.client.RestClient
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID

class RestOrchestrationFinishEventPublisherTest {

	private lateinit var restClient: RestClient
	private lateinit var mockServer: MockRestServiceServer
	private lateinit var publisher: RestOrchestrationFinishEventPublisher

	@BeforeEach
	fun setUp() {
		val httpClient = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.connectTimeout(Duration.ofSeconds(10))
			.build()
		val requestFactory = BufferingClientHttpRequestFactory(JdkClientHttpRequestFactory(httpClient))
		val clientBuilder = RestClient.builder()
			.baseUrl("http://localhost:9090")
			.requestFactory(requestFactory)
		mockServer = MockRestServiceServer.bindTo(clientBuilder).build()
		restClient = clientBuilder.build()
		publisher = RestOrchestrationFinishEventPublisher(restClient)
	}

	@AfterEach
	fun tearDown() {
		mockServer.verify()
	}

	@Test
	fun publishSuccess_postsFinishWithSucceededStatusAndAccepts204() {
		val correlationId = UUID.randomUUID()
		val jobPostingUuid = UUID.randomUUID()
		mockServer.expect(requestTo("http://localhost:9090/events-queue/finish"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("SUCCEEDED"))
			.andRespond(withNoContent())
		publisher.publishSuccess(correlationId, jobPostingUuid, EvaluationStatus.RELEVANT)
	}

	@Test
	fun publishFailure_postsFinishWithFailedStatusAndExecutionLogAndAccepts204() {
		val correlationId = UUID.randomUUID()
		val jobPostingUuid = UUID.randomUUID()
		val executionLog = "evaluation failed for test"
		mockServer.expect(requestTo("http://localhost:9090/events-queue/finish"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.status").value("FAILED"))
			.andExpect(jsonPath("$.executionLog").value(executionLog))
			.andRespond(withNoContent())
		publisher.publishFailure(correlationId, jobPostingUuid, executionLog)
	}
}
