package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("joposcragent.settings-manager")
data class SettingsManagerProperties(
	val baseUrl: String,
)
