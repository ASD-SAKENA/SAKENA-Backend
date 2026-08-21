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
          AND b.cancelledAt IS NULL
        ORDER BY b.startsAt
        """
    )
    fun findAllIntersecting(
        @Param("facilityId") facilityId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<FacilityBookingEntity>

    /** Weekly quota — a cancelled booking must not count against the resident. */
    @Query(
        """
        SELECT COUNT(b) FROM FacilityBookingEntity b
        WHERE b.facilityId = :facilityId AND b.bookedBy = :bookedBy
          AND b.startsAt >= :from AND b.startsAt < :to
          AND b.cancelledAt IS NULL
        """
    )
    fun countActiveInWeek(
        @Param("facilityId") facilityId: UUID,
        @Param("bookedBy") bookedBy: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): Long

    @Query(
        """
        SELECT b FROM FacilityBookingEntity b
        WHERE b.bookedBy = :bookedBy
          AND b.startsAt >= :from
          AND b.cancelledAt IS NULL
          AND b.facilityId IN (
              SELECT f.id FROM FacilityEntity f WHERE f.buildingId = :buildingId
          )
        ORDER BY b.startsAt
        """
    )
    fun findUpcomingByResidentInBuilding(
        @Param("bookedBy") bookedBy: UUID,
        @Param("buildingId") buildingId: UUID,
        @Param("from") from: Instant,
    ): List<FacilityBookingEntity>
}
