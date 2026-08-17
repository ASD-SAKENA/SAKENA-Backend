package com.sakena.facility.infrastructure.persistence

import com.sakena.facility.domain.FacilityBookingRepository
import com.sakena.facility.domain.model.BookingId
import com.sakena.facility.domain.model.FacilityBooking
import com.sakena.facility.domain.model.FacilityId
import com.sakena.property.domain.model.BuildingId
import com.sakena.user.domain.UserId
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class FacilityBookingRepositoryAdapter(
    private val jpaRepository: FacilityBookingJpaRepository,
) : FacilityBookingRepository {

    override fun save(booking: FacilityBooking): FacilityBooking {
        val saved = jpaRepository.save(toEntity(booking))
        return toDomain(saved)
    }

    override fun findById(id: BookingId): FacilityBooking? =
        jpaRepository.findByIdOrNull(id.value)?.let(::toDomain)

    override fun findAllForFacilityBetween(
        facilityId: FacilityId,
        from: Instant,
        to: Instant,
    ): List<FacilityBooking> =
        jpaRepository.findAllIntersecting(facilityId.value, from, to).map(::toDomain)

    override fun countOverlapping(facilityId: FacilityId, startsAt: Instant, endsAt: Instant): Long =
        jpaRepository.countOverlapping(facilityId.value, startsAt, endsAt)

    override fun countByResidentBetween(
        facilityId: FacilityId,
        residentId: UserId,
        from: Instant,
        to: Instant,
    ): Long =
        jpaRepository
            .countByFacilityIdAndBookedByAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                facilityId.value,
                residentId.value,
                from,
                to,
            )

    override fun findUpcomingByResidentInBuilding(
        residentId: UserId,
        buildingId: BuildingId,
        from: Instant,
    ): List<FacilityBooking> =
        jpaRepository
            .findUpcomingByResidentInBuilding(residentId.value, buildingId.value, from)
            .map(::toDomain)

    override fun deleteById(id: BookingId) =
        jpaRepository.deleteById(id.value)

    private fun toEntity(booking: FacilityBooking): FacilityBookingEntity =
        FacilityBookingEntity(
            id = booking.id.value,
            facilityId = booking.facilityId.value,
            bookedBy = booking.bookedBy.value,
            startsAt = booking.startsAt,
            endsAt = booking.endsAt,
            createdAt = booking.createdAt,
        )

    private fun toDomain(entity: FacilityBookingEntity): FacilityBooking =
        FacilityBooking.reconstitute(
            id = BookingId(entity.id),
            facilityId = FacilityId(entity.facilityId),
            bookedBy = UserId(entity.bookedBy),
            startsAt = entity.startsAt,
            endsAt = entity.endsAt,
            createdAt = entity.createdAt,
        )
}
