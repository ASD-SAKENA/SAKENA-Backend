package com.sakena.poll.application.command

import com.sakena.poll.domain.model.PollOptionId

data class CreatePollCommand(
    val question: String,
    val options: List<String>,
)

data class CastVoteCommand(
    val optionId: PollOptionId,
)
