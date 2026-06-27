package com.sakena.user.domain

interface UserRepository {
    fun save(user: User): User
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}
