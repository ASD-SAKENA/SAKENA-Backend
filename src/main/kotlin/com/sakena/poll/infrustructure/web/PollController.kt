package com.sakena.poll.infrastructure.web

import com.sakena.poll.application.PollService
import com.sakena.poll.domain.model.PollId
import com.sakena.poll.infrastructure.web.dto.CastVoteRequest
import com.sakena.poll.infrastructure.web.dto.CreatePollRequest
import com.sakena.poll.infrastructure.web.dto.PollResponse
import com.sakena.user.application.ProfileService
import com.sakena.user.domain.UserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST adapter for the Poll bounded context. Every response carries the live
 * tally plus whether the caller has already voted, so the client can render
 * the results view immediately after a vote.
 */
@RestController
@RequestMapping("/api/v1/polls")
@Tag(name = "Polls", description = "Manager-created polls and resident voting")
@SecurityRequirement(name = "bearerAuth")
class PollController(
    private val pollService: PollService,
    private val profileService: ProfileService,
) {

    @Operation(summary = "List polls with live results, newest first")
    @GetMapping
    fun list(): List<PollResponse> =
        pollService.getAll(getCurrentUserId()).map(PollResponse::from)

    @Operation(summary = "Get one poll with its live results")
    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): PollResponse =
        PollResponse.from(pollService.getResults(PollId.from(id), getCurrentUserId()))

    @Operation(summary = "Create a poll (manager)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreatePollRequest): PollResponse {
        val poll = pollService.create(request.toCommand(), getCurrentUserId())
        return PollResponse.from(pollService.getResults(poll.id, getCurrentUserId()))
    }

    @Operation(summary = "Cast your single vote and receive the updated tally")
    @PostMapping("/{id}/votes")
    fun vote(
        @PathVariable id: String,
        @Valid @RequestBody request: CastVoteRequest,
    ): PollResponse =
        PollResponse.from(
            pollService.vote(PollId.from(id), request.toCommand(), getCurrentUserId()),
        )

    @Operation(summary = "Withdraw your vote and receive the updated tally")
    @DeleteMapping("/{id}/votes")
    fun withdrawVote(@PathVariable id: String): PollResponse =
        PollResponse.from(
            pollService.withdrawVote(PollId.from(id), getCurrentUserId()),
        )

    @Operation(summary = "Close a poll so it stops accepting votes (manager)")
    @PostMapping("/{id}/close")
    fun close(@PathVariable id: String): PollResponse {
        val poll = pollService.close(PollId.from(id))
        return PollResponse.from(pollService.getResults(poll.id, getCurrentUserId()))
    }

    private fun getCurrentUserId(): UserId {
        val username = SecurityContextHolder.getContext().authentication.name
        val user = profileService.getUserByUsername(username)
            ?: throw RuntimeException("User not found")
        return user.id
    }
}
