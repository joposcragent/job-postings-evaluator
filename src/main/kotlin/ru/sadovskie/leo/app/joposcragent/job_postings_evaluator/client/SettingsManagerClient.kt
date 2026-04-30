package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.ReferenceContext
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.RelevanceThresholdItem
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.RelevanceThresholdsList

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
		"/relevance-thresholds/list",
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun getRelevanceThresholdsList(): RelevanceThresholdsList

	@GetMapping(
		"/relevance-thresholds/{type}",
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun getRelevanceThreshold(
		@PathVariable("type") type: String = "CONTENT",
	): RelevanceThresholdItem
}
