package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.ReferenceContext
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.SearchQueryItemResponse
import java.util.UUID

@FeignClient(
	name = "settingsManager",
	url = "\${joposcragent.settings-manager.base-url}",
	primary = false,
)
interface SettingsManagerClient {

	@GetMapping(
		"/reference-context",
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun getReferenceContext(): ReferenceContext

	@GetMapping(
		"/search-query/{entityUuid}",
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun getSearchQuery(@PathVariable("entityUuid") entityUuid: UUID): SearchQueryItemResponse
}
