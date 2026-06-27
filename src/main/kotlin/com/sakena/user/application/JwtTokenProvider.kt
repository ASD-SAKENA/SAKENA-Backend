package com.sakena.user.application

interface JwtTokenProvider {
    fun generateToken(username: String, role: String): String
    fun validateToken(token: String): Boolean
    fun extractUsername(token: String): String
}
