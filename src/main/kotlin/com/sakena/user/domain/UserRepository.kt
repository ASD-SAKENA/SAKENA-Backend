package com.sakena.user.domain

import com.sakena.property.domain.model.BuildingId

interface UserRepository {
    fun save(user: User): User
    fun findAll(): List<User>
    fun findAllByIds(ids: Set<UserId>): List<User>
    fun findByUsername(username: String): User?
    fun findByEmail(email: String): User?
    fun findById(id: UserId): User?
    /** Managers administering a building — a building may have several, or none. */
    fun findManagersOfBuilding(buildingId: BuildingId): List<User>

    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}
