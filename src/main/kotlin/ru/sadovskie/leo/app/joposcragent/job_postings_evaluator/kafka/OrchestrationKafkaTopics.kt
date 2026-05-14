package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.kafka

object OrchestrationKafkaTopics {
	const val JOB_POSTING_EVALUATE = "async-job.job-posting-evaluate"
}

object JobPostingEvaluateMessageTypes {
	const val BEGIN = "async-job.job-posting-evaluate-begin"
	const val RESULT = "async-job.job-posting-evaluate-result"
}
