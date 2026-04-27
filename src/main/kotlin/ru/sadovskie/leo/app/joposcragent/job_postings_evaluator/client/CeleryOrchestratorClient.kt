package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.FinishEventRequest

@FeignClient(
	name = "celeryOrchestrator",
	url = "\${celery.orchestrator.base-url}",
	primary = false,
)
interface CeleryOrchestratorClient {

	@PostMapping(
		"/events-queue/finish",
		consumes = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun finishEvent(@RequestBody body: FinishEventRequest)
}
