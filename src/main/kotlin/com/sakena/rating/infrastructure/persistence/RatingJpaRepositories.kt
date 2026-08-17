package com.sakena.rating.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface StaffRatingJpaRepository : JpaRepository<StaffRatingEntity, UUID> {

    @Query(
        """
        SELECT r.staffId AS staffId, AVG(r.score) AS average
        FROM StaffRatingEntity r
        WHERE r.staffId IN :staffIds
        GROUP BY r.staffId
        """
    )
    fun findAverageByStaffIds(@Param("staffIds") staffIds: Collection<UUID>): List<StaffAverage>
}

/** Projection of the per-staff average-score query. */
interface StaffAverage {
    fun getStaffId(): UUID

    fun getAverage(): Double
}
