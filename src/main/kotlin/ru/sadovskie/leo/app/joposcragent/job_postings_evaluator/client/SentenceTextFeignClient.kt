package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.client

import org.springframework.cloud.openfeign.FeignClient
import ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.client.sentence.api.TextApi

@FeignClient(
	name = "sentenceText",
	url = "\${joposcragent.sentence-transformer.base-url}",
	contextId = "sentenceText",
	primary = false,
)
interface SentenceTextFeignClient : TextApi
