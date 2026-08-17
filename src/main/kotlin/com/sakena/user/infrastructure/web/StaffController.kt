package com.sakena.user.infrastructure.web

import com.sakena.user.application.StaffDirectoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Lets a manager see who is assignable to a service request, without the admin-only full user list. */
@PreAuthorize("hasRole('MANAGER')")
@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Staff", description = "Active service staff, for assigning service requests (manager only)")
@SecurityRequirement(name = "bearerAuth")
class StaffController(
    private val staffDirectoryService: StaffDirectoryService,
) {

    @GetMapping
    @Operation(summary = "List active service staff")
    fun list(): List<UserSummaryResponse> =
        staffDirectoryService.getActiveStaff().map(UserSummaryResponse::from)
}
