package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.web

import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.dto.JobPostingsUidsEvaluatedList
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.dto.JobPostingsUidsList
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.service.EvaluateSyncService
import java.util.UUID

@RestController
class EvaluateController(
	private val evaluateSyncService: EvaluateSyncService,
) {

	@PostMapping(
		"/evaluate/sync",
		consumes = [MediaType.APPLICATION_JSON_VALUE],
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun evaluateSync(@RequestBody body: JobPostingsUidsList): JobPostingsUidsEvaluatedList =
		evaluateSyncService.evaluateSync(body)

	@PostMapping("/evaluate/async")
	fun evaluateAsync(): Nothing {
		throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED)
	}

	@GetMapping("/evaluate/async/{jobUuid}")
	fun getAsyncStatus(@PathVariable jobUuid: UUID): Nothing {
		throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED)
	}
}
