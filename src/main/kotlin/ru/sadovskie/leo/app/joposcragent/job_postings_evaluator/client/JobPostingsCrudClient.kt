package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.ApiEvaluationStatus
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.JobPostingsItem
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.JobPostingsItemPatch
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.JobPostingsList
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.UuidsListRequest
import java.util.UUID

@FeignClient(
	name = "jobPostingsCrud",
	url = "\${joposcragent.job-postings-crud.base-url}",
	primary = false,
)
interface JobPostingsCrudClient {

	@GetMapping(
		"/job-postings/{jobPostingUuid}",
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun getByUuid(@PathVariable("jobPostingUuid") jobPostingUuid: UUID): JobPostingsItem

	@GetMapping(
		"/job-postings/list",
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun list(
		@RequestParam(required = false) uuid: UUID? = null,
		@RequestParam(required = false) uid: String? = null,
		@RequestParam(required = false) title: String? = null,
		@RequestParam(required = false) company: String? = null,
		@RequestParam(required = false, name = "evaluationStatus")
		evaluationStatuses: List<ApiEvaluationStatus>? = null,
		@RequestParam(defaultValue = "1") page: Int = 1,
		@RequestParam(defaultValue = "20") size: Int = 20,
	): JobPostingsList

	@PostMapping(
		"/job-postings/search-query/by-uuids",
		consumes = [MediaType.APPLICATION_JSON_VALUE],
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun findByUuids(@RequestBody body: UuidsListRequest): JobPostingsList

	@PatchMapping(
		"/job-postings/{jobPostingUuid}",
		consumes = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun patch(
		@PathVariable("jobPostingUuid") jobPostingUuid: UUID,
		@RequestBody body: JobPostingsItemPatch,
	)
}
