package com.sakena.user.application

import com.sakena.user.domain.Role
import com.sakena.user.domain.User
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
}
