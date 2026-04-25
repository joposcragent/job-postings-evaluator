package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.orchestration

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class RestOrchestrationFinishEventPublisher(
	@Qualifier("celeryOrchestrator") private val restClient: RestClient,
) : OrchestrationFinishEventPublisher {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun publishSuccess(
		correlationId: UUID,
		jobPostingUuid: UUID,
		evaluationStatus: EvaluationStatus,
	) {
		val body = linkedMapOf<String, Any?>(
			"correlationId" to correlationId,
			"createdAt" to OffsetDateTime.now(ZoneOffset.UTC),
			"jobPostingUuid" to jobPostingUuid,
			"status" to "SUCCEEDED",
			"result" to mapOf("evaluationStatus" to evaluationStatus.literal),
		)
		postFinish(body)
	}

	override fun publishFailure(
		correlationId: UUID,
		jobPostingUuid: UUID,
		executionLog: String,
	) {
		val body = linkedMapOf<String, Any?>(
			"correlationId" to correlationId,
			"createdAt" to OffsetDateTime.now(ZoneOffset.UTC),
			"jobPostingUuid" to jobPostingUuid,
			"status" to "FAILED",
			"executionLog" to executionLog,
			// task.finish requires snapshot result + finishEventStatus (from status)
			"result" to mapOf("evaluationFailed" to true),
		)
		postFinish(body)
	}

	private fun postFinish(body: Map<String, Any?>) {
		try {
			restClient.post()
				.uri("/events-queue/finish")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.toBodilessEntity()
		} catch (e: RestClientResponseException) {
			log.error(
				"Orchestrator returned HTTP {} on POST /events-queue/finish: {}",
				e.statusCode.value(),
				e.responseBodyAsString,
				e,
			)
			throw IllegalStateException(
				"Orchestrator returned ${e.statusCode.value()}: ${e.responseBodyAsString}",
				e,
			)
		} catch (e: Exception) {
			log.error(
				"Call to orchestrator failed (POST /events-queue/finish). {}",
				e.message ?: e.toString(),
				e,
			)
			throw e
		}
	}
}
