package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.CosineSimilarityResponse
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.TextCorpus
import ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto.VectorsPair

@FeignClient(
	name = "sentenceTransformer",
	url = "\${joposcragent.sentence-transformer.base-url}",
	primary = false,
)
interface SentenceTransformerClient {

	@PostMapping(
		"/text/vectorize",
		consumes = [MediaType.APPLICATION_JSON_VALUE],
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun vectorize(@RequestBody body: TextCorpus): List<Double>

	@PostMapping(
		"/vectors/cosine-similarity",
		consumes = [MediaType.APPLICATION_JSON_VALUE],
		produces = [MediaType.APPLICATION_JSON_VALUE],
	)
	fun cosineSimilarity(@RequestBody body: VectorsPair): CosineSimilarityResponse
}
