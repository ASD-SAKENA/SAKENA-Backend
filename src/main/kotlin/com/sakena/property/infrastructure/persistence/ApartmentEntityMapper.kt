package com.sakena.property.infrastructure.persistence

import com.sakena.property.domain.model.Apartment
import com.sakena.property.domain.model.ApartmentId
import com.sakena.property.domain.model.BuildingId

internal object ApartmentEntityMapper {

    fun toEntity(apartment: Apartment): ApartmentEntity =
        ApartmentEntity(
            id = apartment.id.value,
            buildingId = apartment.buildingId.value,
            unitNumber = apartment.unitNumber,
            floorNumber = apartment.floorNumber,
            areaSquareMeters = apartment.areaSquareMeters,
            bedrooms = apartment.bedrooms,
            createdAt = apartment.createdAt,
            updatedAt = apartment.updatedAt,
        )

    fun toDomain(entity: ApartmentEntity): Apartment =
        Apartment.reconstitute(
            id = ApartmentId(entity.id),
            buildingId = BuildingId(entity.buildingId),
            unitNumber = entity.unitNumber,
            floorNumber = entity.floorNumber,
            areaSquareMeters = entity.areaSquareMeters,
            bedrooms = entity.bedrooms,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
