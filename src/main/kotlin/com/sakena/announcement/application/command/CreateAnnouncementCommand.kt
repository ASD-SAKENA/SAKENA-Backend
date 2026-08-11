package com.sakena.announcement.application.command

data class CreateAnnouncementCommand(
    val title: String,
    val body: String,
)
