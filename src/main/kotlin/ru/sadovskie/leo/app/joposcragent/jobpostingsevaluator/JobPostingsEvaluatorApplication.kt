package ru.sadovskie.leo.app.joposcragent.jobpostingsevaluator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class JobPostingsEvaluatorApplication

fun main(args: Array<String>) {
	runApplication<JobPostingsEvaluatorApplication>(*args)
}
