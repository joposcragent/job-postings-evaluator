package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class JobPostingsEvaluatorApplicationTests {

	@Test
	fun contextLoads() {
	}

}
