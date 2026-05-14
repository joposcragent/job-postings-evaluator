package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service.EvaluationService
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@Component
@ConditionalOnProperty(name = ["app.kafka.consumer-enabled"], havingValue = "true", matchIfMissing = true)
class JobPostingEvaluateBeginKafkaListener(
	private val jsonMapper: JsonMapper,
	private val evaluationService: EvaluationService,
) {
	private val log = LoggerFactory.getLogger(javaClass)

	@KafkaListener(
		topics = [OrchestrationKafkaTopics.JOB_POSTING_EVALUATE],
		groupId = "\${app.kafka.evaluate-consumer-group}",
	)
	fun onMessage(record: ConsumerRecord<String, String>) {
		val type = record.headers().lastHeader("type")?.value()?.toString(Charsets.UTF_8)
			?: record.value()?.let { parseTypeFromJson(it) }
			?: return
		if (type != JobPostingEvaluateMessageTypes.BEGIN) {
			return
		}
		val root = runCatching { jsonMapper.readTree(record.value()) }.getOrElse {
			log.warn("job-posting-evaluate-begin: invalid json: {}", it.message)
			return
		}
		val payload = root.get("payload") ?: run {
			log.warn("job-posting-evaluate-begin: missing payload")
			return
		}
		val jobUuid = payload.uuid("jobUuid") ?: run {
			log.warn("job-posting-evaluate-begin: missing jobUuid")
			return
		}
		val jobPostingUuid = payload.uuid("jobPostingUuid") ?: run {
			log.warn("job-posting-evaluate-begin: missing jobPostingUuid")
			return
		}
		evaluationService.evaluateFromKafkaBegin(jobUuid, jobPostingUuid)
	}

	private fun parseTypeFromJson(json: String): String? =
		runCatching {
			jsonMapper.readTree(json).path("headers").path("type").asText(null)
		}.getOrNull()

	private fun JsonNode.uuid(field: String): UUID? =
		get(field)?.takeIf { !it.isNull && it.asText().isNotBlank() }?.let { UUID.fromString(it.asText()) }
}
