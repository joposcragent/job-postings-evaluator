package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

// --- job-postings-evaluator API ---

data class UuidsListRequest(
	@field:NotEmpty
	val list: List<UUID>,
)

data class BatchAsyncProcessingRequest(
	@field:NotNull
	@field:Min(1)
	val size: Int,
)

data class SingleEvaluationStatusResponse(
	val status: ApiEvaluationStatus,
)

data class SyncEvaluationResultItem(
	val uuid: UUID,
	val status: ApiEvaluationStatus,
)

enum class ApiEvaluationStatus {
	NEW,
	PENDING,
	IRRELEVANT,
	RELEVANT,
}

// --- job-postings-crud ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class JobPostingsItem(
	val uuid: UUID,
	val searchQueryUuid: UUID,
	@JsonProperty("contentVector")
	val contentVector: List<Double>? = null,
	val content: String? = null,
	val evaluationStatus: ApiEvaluationStatus? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class JobPostingsList(
	val list: List<JobPostingsItem> = emptyList(),
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class JobPostingsItemPatch(
	val contentVector: List<Double>? = null,
	val relevance: Double? = null,
	val evaluationStatus: ApiEvaluationStatus? = null,
)

// --- settings-manager ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class ReferenceContext(
	val context: String,
	val vector: List<Double>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchQueryItemResponse(
	val contentRelevance: Double,
	val notificationRelevance: Double? = null,
)

// --- sentence-transformer ---

data class TextCorpus(
	val text: String,
)

data class VectorsPair(
	val left: List<Double>,
	val right: List<Double>,
)

data class CosineSimilarityResponse(
	val similarity: Double,
)

// --- celery-orchestrator ---

data class FinishEventRequest(
	val correlationId: UUID,
	val status: String,
	@JsonProperty("createdAt")
	val createdAt: Instant,
	val jobPostingUuid: UUID? = null,
	val executionLog: String? = null,
)
