package com.sakena.user.infrastructure.persistence

import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Repository

@Repository
class UserRepositoryAdapter(
    private val jpaRepo: UserRepositoryJpa
) : UserRepository {

    override fun save(user: User): User {
        val entity = toJpaEntity(user)
        val saved = jpaRepo.save(entity)
        return toDomain(saved)
    }

    override fun findAll(): List<User> {
        return jpaRepo.findAll().map { toDomain(it) }
    }

    override fun findByUsername(username: String): User? {
        return jpaRepo.findByUsername(username)?.let { toDomain(it) }
    }

    override fun findByEmail(email: String): User? {
        return jpaRepo.findByEmail(email)?.let { toDomain(it) }
    }

    override fun findById(id: UserId): User? {
        return jpaRepo.findById(id.value).orElse(null)?.let { toDomain(it) }
    }

    override fun existsByUsername(username: String): Boolean =
        jpaRepo.existsByUsername(username)

    override fun existsByEmail(email: String): Boolean =
        jpaRepo.existsByEmail(email)

    private fun toJpaEntity(user: User): UserJpaEntity {
        return UserJpaEntity(
            id = user.id.value,
            username = user.username,
            email = user.email,
            passwordHash = user.passwordHash,
            role = user.role,
            createdAt = user.createdAt,
            updatedAt = user.updatedAt,
            active = user.active,
            specialty = user.specialty
        )
    }

    private fun toDomain(entity: UserJpaEntity): User {
        return User.reconstitute(
            id = UserId(entity.id),
            username = entity.username,
            email = entity.email,
            passwordHash = entity.passwordHash,
            role = entity.role,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            active = entity.active,
            specialty = entity.specialty
        )
    }
}
