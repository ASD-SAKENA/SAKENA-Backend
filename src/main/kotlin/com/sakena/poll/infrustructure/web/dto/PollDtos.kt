package com.sakena.poll.infrastructure.web.dto

import com.sakena.poll.application.command.CastVoteCommand
import com.sakena.poll.application.command.CreatePollCommand
import com.sakena.poll.domain.model.PollOptionId
import com.sakena.poll.domain.model.PollResults
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreatePollRequest(
    @field:NotBlank(message = "question must not be blank")
    @field:Size(max = 300, message = "question must be at most 300 characters")
    val question: String,

    @field:Size(min = 2, max = 10, message = "a poll needs between 2 and 10 options")
    val options: List<String>,
) {
    fun toCommand() = CreatePollCommand(question = question, options = options)
}

data class CastVoteRequest(
    @field:NotNull(message = "optionId must not be null")
    val optionId: UUID,
) {
    fun toCommand() = CastVoteCommand(optionId = PollOptionId(optionId))
}

data class PollOptionResultResponse(
    val optionId: UUID,
    val label: String,
    val votes: Long,
    val percentage: Double,
)

data class PollResponse(
    val id: UUID,
    val question: String,
    val open: Boolean,
    val createdAt: Instant,
    val closedAt: Instant?,
    val totalVotes: Long,
    val hasVoted: Boolean,
    val myOptionId: UUID?,
    val options: List<PollOptionResultResponse>,
) {
    companion object {
        fun from(results: PollResults) = PollResponse(
            id = results.poll.id.value,
            question = results.poll.question,
            open = results.poll.open,
            createdAt = results.poll.createdAt,
            closedAt = results.poll.closedAt,
            totalVotes = results.totalVotes,
            hasVoted = results.hasVoted,
            myOptionId = results.myOptionId?.value,
            options = results.options.map {
                PollOptionResultResponse(
                    optionId = it.optionId.value,
                    label = it.label,
                    votes = it.votes,
                    percentage = it.percentage,
                )
            },
        )
    }
}
