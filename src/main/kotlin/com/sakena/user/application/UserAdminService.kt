package com.sakena.user.application

import com.sakena.shared.domain.EntityNotFoundException
import com.sakena.user.domain.Role
import com.sakena.user.domain.User
import com.sakena.user.domain.UserId
import com.sakena.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UserAdminService(
    private val userRepository: UserRepository
) {

    @Transactional(readOnly = true)
    fun getUsers(role: Role? = null): List<User> {
        val users = userRepository.findAll()
        return role?.let { r -> users.filter { it.role == r } } ?: users
    }

    fun changeActiveStatus(userId: UserId, active: Boolean): User {
        val user = userRepository.findById(userId)
            ?: throw EntityNotFoundException("User not found: ${userId.value}")
        val updated = if (active) user.activate() else user.deactivate()
        return userRepository.save(updated)
    }

    fun changeSpecialty(userId: UserId, specialty: String?): User {
        val user = userRepository.findById(userId)
            ?: throw EntityNotFoundException("User not found: ${userId.value}")
        return userRepository.save(user.withSpecialty(specialty))
    }
}
