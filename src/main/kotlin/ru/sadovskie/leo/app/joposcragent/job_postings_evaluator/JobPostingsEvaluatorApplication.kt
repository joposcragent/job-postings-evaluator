package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.kafka.annotation.EnableKafka

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
@EnableKafka
class JobPostingsEvaluatorApplication

fun main(args: Array<String>) {
	runApplication<JobPostingsEvaluatorApplication>(*args)
}
