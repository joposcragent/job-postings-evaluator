package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("joposcragent")
data class JoposcragentProperties(
	val settingsManagerBaseUrl: String,
	val sentenceTransformerBaseUrl: String,
)
