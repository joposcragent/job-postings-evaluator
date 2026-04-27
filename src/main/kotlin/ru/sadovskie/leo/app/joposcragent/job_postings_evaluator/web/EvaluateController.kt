package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import jakarta.validation.Valid
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.BatchAsyncProcessingRequest
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.SyncEvaluationResultItem
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.UuidsListRequest
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service.AsyncEvaluationRunner
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service.EvaluationService
import java.util.UUID

@Validated
@RestController
@RequestMapping
class EvaluateController(
	private val evaluation: EvaluationService,
	private val asyncRunner: AsyncEvaluationRunner,
) {

	private val log = LoggerFactory.getLogger(javaClass)

	@PostMapping("/evaluate/sync/list")
	fun syncList(@RequestBody @Valid body: UuidsListRequest): List<SyncEvaluationResultItem> {
		log.info("http accepted evaluate sync list requestedUuids={}", body.list.size)
		return evaluation.evaluateSyncList(body)
	}

	@PostMapping("/evaluate/sync/{jobPostingUuid}")
	fun syncOne(
		@PathVariable jobPostingUuid: UUID,
		@RequestHeader(name = "X-Joposcragent-correlationId", required = false) correlationId: UUID?,
	): Map<String, Any> {
		log.info(
			"http accepted evaluate sync one jobPostingUuid={} correlationId={}",
			jobPostingUuid,
			correlationId?.toString() ?: "-",
		)
		val st = evaluation.evaluateSyncOne(jobPostingUuid, correlationId)
		return mapOf("status" to st)
	}

	@PostMapping("/evaluate/async/list")
	fun asyncList(@RequestBody @Valid body: UuidsListRequest): ResponseEntity<Void> {
		log.info("http accepted evaluate async list requestedUuids={}", body.list.size)
		asyncRunner.runList(body)
		return ResponseEntity.ok().build()
	}

	@PostMapping("/evaluate/async/{jobPostingUuid}")
	fun asyncOne(
		@PathVariable jobPostingUuid: UUID,
		@RequestHeader(name = "X-Joposcragent-correlationId", required = false) correlationId: UUID?,
	): ResponseEntity<Void> {
		log.info(
			"http accepted evaluate async one jobPostingUuid={} correlationId={}",
			jobPostingUuid,
			correlationId?.toString() ?: "-",
		)
		asyncRunner.runOne(jobPostingUuid, correlationId)
		return ResponseEntity.ok().build()
	}

	@PostMapping("/evaluate/async/batch")
	fun asyncBatch(
		@Valid @RequestBody request: BatchAsyncProcessingRequest,
	): ResponseEntity<Void> {
		log.info("http accepted evaluate async batch size={}", request.size)
		asyncRunner.runBatch(request.size)
		return ResponseEntity.ok().build()
	}
}
