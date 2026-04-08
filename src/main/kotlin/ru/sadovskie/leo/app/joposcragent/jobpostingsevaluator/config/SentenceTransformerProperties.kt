package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("joposcragent.sentence-transformer")
data class SentenceTransformerProperties(
	val baseUrl: String,
)
