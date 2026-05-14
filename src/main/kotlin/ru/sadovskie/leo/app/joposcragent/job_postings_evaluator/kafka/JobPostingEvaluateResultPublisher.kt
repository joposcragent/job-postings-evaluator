package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.kafka

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.util.UUID

@Component
class JobPostingEvaluateResultPublisher(
	private val kafkaTemplate: KafkaTemplate<String, String>,
	private val jsonMapper: JsonMapper,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun publishSucceeded(
		jobUuid: UUID,
		jobPostingUuid: UUID,
		evaluationStatus: EvaluationStatus,
		relevance: Double,
	) {
		val key = jobUuid.toString()
		val createdAt = OffsetDateTime.now().toString()
		val schemaVersion = "1.0"
		val statusStr = evaluationStatus.name
		val result = jsonMapper.createObjectNode().apply {
			put("status", statusStr)
			put("relevance", relevance)
		}
		val payload = jsonMapper.createObjectNode().apply {
			put("jobUuid", jobUuid.toString())
			put("status", "SUCCEEDED")
			set("result", result)
			putNull("context")
			put("jobPostingUuid", jobPostingUuid.toString())
			put("evaluationStatus", statusStr)
			put("relevance", relevance)
		}
		sendEnvelope(key, createdAt, schemaVersion, JobPostingEvaluateMessageTypes.RESULT, payload)
	}

	fun publishFailed(jobUuid: UUID, jobPostingUuid: UUID, errorText: String) {
		val key = jobUuid.toString()
		val createdAt = OffsetDateTime.now().toString()
		val schemaVersion = "1.0"
		val payload = jsonMapper.createObjectNode().apply {
			put("jobUuid", jobUuid.toString())
			put("status", "FAILED")
			put("result", errorText)
			putNull("context")
			put("jobPostingUuid", jobPostingUuid.toString())
		}
		sendEnvelope(key, createdAt, schemaVersion, JobPostingEvaluateMessageTypes.RESULT, payload)
	}

	private fun sendEnvelope(
		messageKey: String,
		createdAt: String,
		schemaVersion: String,
		type: String,
		payload: ObjectNode,
	) {
		val headersNode = jsonMapper.createObjectNode().apply {
			put("key", messageKey)
			put("createdAt", createdAt)
			put("type", type)
			put("schemaVersion", schemaVersion)
		}
		val root = jsonMapper.createObjectNode().apply {
			set("headers", headersNode)
			set("payload", payload)
		}
		val json = jsonMapper.writeValueAsString(root)
		val record = ProducerRecord(OrchestrationKafkaTopics.JOB_POSTING_EVALUATE, messageKey, json)
		record.headers().add(RecordHeader("type", type.toByteArray(StandardCharsets.UTF_8)))
		record.headers().add(RecordHeader("key", messageKey.toByteArray(StandardCharsets.UTF_8)))
		record.headers().add(RecordHeader("createdAt", createdAt.toByteArray(StandardCharsets.UTF_8)))
		record.headers().add(RecordHeader("schemaVersion", schemaVersion.toByteArray(StandardCharsets.UTF_8)))
		runCatching { kafkaTemplate.send(record).get() }
			.onFailure { log.error("kafka publish failed type={} key={}: {}", type, messageKey, it.toString(), it) }
	}
}
