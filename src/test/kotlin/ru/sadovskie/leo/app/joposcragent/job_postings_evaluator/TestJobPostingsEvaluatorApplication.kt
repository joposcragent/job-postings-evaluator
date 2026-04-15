package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<JobPostingsEvaluatorApplication>().with(TestcontainersConfiguration::class).run(*args)
}
