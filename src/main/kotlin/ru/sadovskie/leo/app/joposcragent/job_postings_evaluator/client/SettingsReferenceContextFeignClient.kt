package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client

import org.springframework.cloud.openfeign.FeignClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.settings.api.ReferenceContextApi

@FeignClient(
	name = "settingsReferenceContext",
	url = "\${joposcragent.settings-manager.base-url}",
	contextId = "settingsReferenceContext",
	primary = false,
)
interface SettingsReferenceContextFeignClient : ReferenceContextApi
