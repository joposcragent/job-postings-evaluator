package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client

import org.springframework.cloud.openfeign.FeignClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.sentence.api.VectorsApi

@FeignClient(
	name = "sentenceVectors",
	url = "\${joposcragent.sentence-transformer.base-url}",
	contextId = "sentenceVectors",
	primary = false,
)
interface SentenceVectorsFeignClient : VectorsApi
