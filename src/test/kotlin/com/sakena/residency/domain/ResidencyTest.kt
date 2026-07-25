package com.sakena.residency.domain

import com.sakena.property.domain.model.ApartmentId
import com.sakena.residency.domain.model.Residency
import com.sakena.residency.domain.model.TenancyType
import com.sakena.shared.domain.DomainConflictException
import com.sakena.user.domain.UserId
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResidencyTest {

    private fun residency() = Residency.start(
        apartmentId = ApartmentId.new(),
        residentId = UserId.generate(),
        tenancy = TenancyType.TENANT,
    )

    @Test
    fun `a new residency is active and has no move-out date`() {
        val residency = residency()

        assertTrue(residency.active)
        assertNull(residency.movedOutAt)
    }

    @Test
    fun `ending a residency records the move-out date`() {
        val residency = residency()

        residency.end()

        assertFalse(residency.active)
        assertNotNull(residency.movedOutAt)
    }

    @Test
    fun `a residency cannot be ended twice`() {
        val residency = residency()
        residency.end()

        assertFailsWith<DomainConflictException> { residency.end() }
    }
}
