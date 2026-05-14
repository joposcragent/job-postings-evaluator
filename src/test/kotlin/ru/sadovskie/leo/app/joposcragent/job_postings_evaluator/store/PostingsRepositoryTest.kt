package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.store

import com.zaxxer.hikari.HikariDataSource
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.EvaluationStatus
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.enums.ResponseStatus
import ru.sadovskie.leo.app.joposcragent.jobpostings.jooq.tables.Postings.POSTINGS
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
class PostingsRepositoryTest {

	companion object {
		@Container
		@JvmField
		val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
			.withCopyFileToContainer(
				MountableFile.forClasspathResource("job-postings-test-schema.sql"),
				"/docker-entrypoint-initdb.d/01-schema.sql",
			)
	}

	private var dataSource: HikariDataSource? = null
	private lateinit var repo: PostingsRepository

	@BeforeEach
	fun setup() {
		val ds = HikariDataSource().apply {
			jdbcUrl = postgres.jdbcUrl
			username = postgres.username
			password = postgres.password
			maximumPoolSize = 2
		}
		dataSource = ds
		val dsl = DSL.using(ds, SQLDialect.POSTGRES)
		repo = PostingsRepository(dsl)
	}

	@AfterEach
	fun tearDown() {
		dataSource?.close()
		dataSource = null
	}

	@Test
	fun `findByUuid returns null when missing`() {
		assertNull(repo.findByUuid(UUID.randomUUID()))
	}

	@Test
	fun `findByUuid maps row`() {
		val id = UUID.fromString("c0000000-0000-0000-0000-000000000001")
		val sq = UUID.fromString("d0000000-0000-0000-0000-000000000002")
		insertMinimal(id, sq, EvaluationStatus.NEW, content = "hello", vector = arrayOf<Float>(1f, 0f, 0f))
		val row = repo.findByUuid(id)!!
		assertEquals(id, row.uuid)
		assertEquals(sq, row.searchQueryUuid)
		assertEquals("hello", row.content)
		assertEquals(listOf(1.0, 0.0, 0.0), row.contentVector)
		assertEquals(EvaluationStatus.NEW, row.evaluationStatus)
	}

	@Test
	fun `findByUuidsEligibleForSync filters statuses`() {
		val n = UUID.fromString("e0000000-0000-0000-0000-000000000001")
		val p = UUID.fromString("e0000000-0000-0000-0000-000000000002")
		val r = UUID.fromString("e0000000-0000-0000-0000-000000000003")
		val sq = UUID.fromString("f0000000-0000-0000-0000-000000000001")
		insertMinimal(n, sq, EvaluationStatus.NEW)
		insertMinimal(p, sq, EvaluationStatus.PENDING)
		insertMinimal(r, sq, EvaluationStatus.RELEVANT)
		val out = repo.findByUuidsEligibleForSync(listOf(n, p, r))
		assertEquals(2, out.size)
		assertTrue(out.any { it.uuid == n })
		assertTrue(out.any { it.uuid == p })
	}

	@Test
	fun `findByUuidsEligibleForSync returns empty for empty input`() {
		assertTrue(repo.findByUuidsEligibleForSync(emptyList()).isEmpty())
	}

	@Test
	fun `listNewPendingOrderedLimit respects limit and order`() {
		val sq = UUID.fromString("a1000000-0000-0000-0000-000000000099")
		val u1 = UUID.fromString("a1000000-0000-0000-0000-000000000001")
		val u2 = UUID.fromString("a1000000-0000-0000-0000-000000000002")
		insertMinimal(u2, sq, EvaluationStatus.NEW)
		insertMinimal(u1, sq, EvaluationStatus.PENDING)
		val first = repo.listNewPendingOrderedLimit(1)
		assertEquals(1, first.size)
		assertEquals(u1, first[0].uuid)
	}

	@Test
	fun `updateAfterEvaluation persists vector relevance and status`() {
		val id = UUID.fromString("b2000000-0000-0000-0000-000000000001")
		val sq = UUID.fromString("b2000000-0000-0000-0000-000000000099")
		insertMinimal(id, sq, EvaluationStatus.NEW)
		repo.updateAfterEvaluation(id, listOf(0.0, 1.0, 0.0), 0.25, EvaluationStatus.IRRELEVANT)
		val dsl = DSL.using(dataSource!!, SQLDialect.POSTGRES)
		val rec = dsl.selectFrom(POSTINGS).where(POSTINGS.UUID.eq(id)).fetchOne()!!
		assertEquals(EvaluationStatus.IRRELEVANT, rec.evaluationStatus)
		assertEquals(0.25f, rec.relevance!!, 1e-5f)
		assertEquals(3, rec.contentVector!!.size)
		assertTrue(rec.updatedAt != null)
	}

	private fun insertMinimal(
		uuid: UUID,
		searchQueryUuid: UUID,
		status: EvaluationStatus,
		content: String? = null,
		vector: Array<Float>? = null,
	) {
		val dsl = DSL.using(dataSource!!, SQLDialect.POSTGRES)
		dsl.insertInto(POSTINGS)
			.set(POSTINGS.UUID, uuid)
			.set(POSTINGS.UID, "uid-$uuid")
			.set(POSTINGS.TITLE, "title")
			.set(POSTINGS.URL, "https://example.com/$uuid")
			.set(POSTINGS.CONTENT, content)
			.set(POSTINGS.CONTENT_VECTOR, vector)
			.set(POSTINGS.SEARCH_QUERY_UUID, searchQueryUuid)
			.set(POSTINGS.EVALUATION_STATUS, status)
			.set(POSTINGS.RESPONSE_STATUS, ResponseStatus.NEW)
			.set(POSTINGS.PUBLICATION_DATE, "2026-01-01")
			.execute()
	}
}
