package com.sakena.residency.domain.model

import com.sakena.property.domain.model.ApartmentId
import com.sakena.shared.domain.DomainConflictException
import com.sakena.shared.domain.DomainValidationException
import com.sakena.user.domain.UserId
import java.time.Instant
import java.util.UUID

/** Value object identifying a [Residency] aggregate. */
@JvmInline
value class ResidencyId(val value: UUID) {

    override fun toString(): String = value.toString()

    companion object {
        fun new(): ResidencyId = ResidencyId(UUID.randomUUID())

        fun from(raw: String): ResidencyId =
            try {
                ResidencyId(UUID.fromString(raw))
            } catch (e: IllegalArgumentException) {
                throw DomainValidationException("'$raw' is not a valid residency id")
            }
    }
}

/** On what basis someone occupies a unit — shown next to the resident's name. */
enum class TenancyType {
    OWNER_OCCUPIER,
    TENANT,
    COMMERCIAL,
}

/**
 * Residency aggregate root — the link between a resident and the unit they
 * occupy, for a period of time. Ending a residency keeps the record instead of
 * deleting it, so a unit's occupancy history survives tenant changes.
 */
class Residency private constructor(
    val id: ResidencyId,
    val apartmentId: ApartmentId,
    val residentId: UserId,
    val tenancy: TenancyType,
    val movedInAt: Instant,
    movedOutAt: Instant?,
) {
    var movedOutAt: Instant? = movedOutAt
        private set

    val active: Boolean get() = movedOutAt == null

    fun end() {
        if (!active) throw DomainConflictException("This residency has already ended")
        movedOutAt = Instant.now()
    }

    companion object {
        fun start(
            apartmentId: ApartmentId,
            residentId: UserId,
            tenancy: TenancyType,
            movedInAt: Instant = Instant.now(),
        ): Residency = Residency(
            id = ResidencyId.new(),
            apartmentId = apartmentId,
            residentId = residentId,
            tenancy = tenancy,
            movedInAt = movedInAt,
            movedOutAt = null,
        )

        /** Rebuilds an aggregate from already-persisted state. No invariants are re-checked. */
        fun reconstitute(
            id: ResidencyId,
            apartmentId: ApartmentId,
            residentId: UserId,
            tenancy: TenancyType,
            movedInAt: Instant,
            movedOutAt: Instant?,
        ): Residency = Residency(id, apartmentId, residentId, tenancy, movedInAt, movedOutAt)
    }
}
