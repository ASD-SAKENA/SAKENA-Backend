package com.sakena.wallet.infrastructure.persistence

import com.sakena.wallet.domain.model.WalletOwnerType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface WalletJpaRepository : JpaRepository<WalletEntity, UUID> {
    fun findByOwnerBuildingId(ownerBuildingId: UUID): WalletEntity?

    @Modifying(flushAutomatically = true)
    @Query(
        value = """
            INSERT INTO wallets (
                id, owner_type, owner_user_id, owner_building_id,
                balance, created_at, updated_at
            )
            VALUES (:id, 'BUILDING', NULL, :buildingId, 0, now(), now())
            ON CONFLICT (owner_building_id)
                WHERE owner_building_id IS NOT NULL
            DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertBuildingWalletIfAbsent(
        @Param("id") id: UUID,
        @Param("buildingId") buildingId: UUID,
    ): Int

    fun findByOwnerUserId(ownerUserId: UUID): WalletEntity?
}
