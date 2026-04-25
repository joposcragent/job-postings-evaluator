package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web

import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.service.EvaluationService
import java.util.UUID

@RestController
@RequestMapping("/evaluate")
@Validated
class EvaluateController(
	private val evaluationService: EvaluationService,
) {

	@PostMapping("/sync/list", produces = [MediaType.APPLICATION_JSON_VALUE])
	fun syncList(@Valid @RequestBody body: UuidsListRequest): List<SyncEvaluationResultItem> =
		evaluationService.evaluateSyncList(body.list)

	@PostMapping("/sync/{jobPostingUuid}", produces = [MediaType.APPLICATION_JSON_VALUE])
	fun syncOne(
		@PathVariable jobPostingUuid: UUID,
		@RequestHeader(name = "X-Joposcragent-correlationId", required = false) correlationId: UUID?,
	): SyncSingleEvaluationResponse =
		SyncSingleEvaluationResponse(evaluationService.evaluateSyncOne(jobPostingUuid, correlationId))

	@PostMapping("/async/list")
	fun asyncList(@Valid @RequestBody body: UuidsListRequest): ResponseEntity<Void> {
		evaluationService.submitAsyncList(body.list)
		return ResponseEntity.ok().build()
	}

	@PostMapping("/async/batch")
	fun asyncBatch(@Valid @RequestBody body: BatchAsyncProcessingParametersRequest): ResponseEntity<Void> {
		evaluationService.submitAsyncBatch(body.size)
		return ResponseEntity.ok().build()
	}

	@PostMapping("/async/{jobPostingUuid}")
	fun asyncOne(
		@PathVariable jobPostingUuid: UUID,
		@RequestHeader(name = "X-Joposcragent-correlationId", required = false) correlationId: UUID?,
	): ResponseEntity<Void> {
		evaluationService.submitAsyncOne(jobPostingUuid, correlationId)
		return ResponseEntity.ok().build()
	}
}
