package com.sakena.facility.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface FacilityBookingJpaRepository : JpaRepository<FacilityBookingEntity, UUID> {

    @Query(
        """
        SELECT b FROM FacilityBookingEntity b
        WHERE b.facilityId = :facilityId AND b.startsAt < :to AND b.endsAt > :from
        ORDER BY b.startsAt
        """
    )
    fun findAllIntersecting(
        @Param("facilityId") facilityId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<FacilityBookingEntity>

    @Query(
        """
        SELECT COUNT(b) FROM FacilityBookingEntity b
        WHERE b.facilityId = :facilityId AND b.startsAt < :endsAt AND b.endsAt > :startsAt
        """
    )
    fun countOverlapping(
        @Param("facilityId") facilityId: UUID,
        @Param("startsAt") startsAt: Instant,
        @Param("endsAt") endsAt: Instant,
    ): Long
}
