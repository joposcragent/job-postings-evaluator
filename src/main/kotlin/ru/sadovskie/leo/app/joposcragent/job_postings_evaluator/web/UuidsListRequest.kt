package ru.sadovskie.leo.app.joposcragent.job_postings_evaluator.web

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class UuidsListRequest(
	@field:NotNull
	@JsonProperty("list")
	val list: List<UUID> = emptyList(),
)
