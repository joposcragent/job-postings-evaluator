package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client

import org.springframework.cloud.openfeign.FeignClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.settings.api.SearchQueryApi

@FeignClient(
	name = "settingsSearchQuery",
	url = "\${joposcragent.settings-manager.base-url}",
	contextId = "settingsSearchQuery",
	primary = false,
)
interface SettingsSearchQueryFeignClient : SearchQueryApi
