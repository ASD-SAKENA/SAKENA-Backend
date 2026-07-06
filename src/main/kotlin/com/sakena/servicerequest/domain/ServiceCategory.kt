package com.sakena.servicerequest.domain

enum class ServiceCategoryGroup(val persianName: String) {
    FACILITIES("تاسیسات"),
    BUILDING("ساختمان"),
    CLEANING("نظافت"),
    SECURITY("امنیت"),
    GREEN_SPACE("فضای سبز"),
    COMMUNICATION("ارتباطات"),
    GENERAL("عمومی")
}

enum class ServiceSubCategory(
    val group: ServiceCategoryGroup,
    val persianName: String
) {
    ELECTRICAL(ServiceCategoryGroup.FACILITIES, "برق"),
    PLUMBING(ServiceCategoryGroup.FACILITIES, "لوله‌کشی"),
    HVAC(ServiceCategoryGroup.FACILITIES, "گرمایش/سرمایش"),
    ELEVATOR(ServiceCategoryGroup.FACILITIES, "آسانسور"),
    GAS(ServiceCategoryGroup.FACILITIES, "گاز"),

    ROOF_WALL(ServiceCategoryGroup.BUILDING, "سقف و دیوار"),
    DOOR_WINDOW(ServiceCategoryGroup.BUILDING, "در و پنجره"),
    FLOORING(ServiceCategoryGroup.BUILDING, "کف‌پوش"),
    FACADE(ServiceCategoryGroup.BUILDING, "نما"),

    CLEANING(ServiceCategoryGroup.CLEANING, "نظافت عمومی"),
    WASTE(ServiceCategoryGroup.CLEANING, "دفع زباله"),
    PESTS(ServiceCategoryGroup.CLEANING, "آفات"),
    TANK_CLEANING(ServiceCategoryGroup.CLEANING, "نظافت مخازن"),

    ENTRANCE(ServiceCategoryGroup.SECURITY, "درب ورودی"),
    CCTV(ServiceCategoryGroup.SECURITY, "دوربین مداربسته"),
    PARKING(ServiceCategoryGroup.SECURITY, "پارکینگ"),
    LIGHTING(ServiceCategoryGroup.SECURITY, "نورپردازی"),

    GARDEN(ServiceCategoryGroup.GREEN_SPACE, "باغچه"),
    LANDSCAPE(ServiceCategoryGroup.GREEN_SPACE, "محوطه"),
    POOL(ServiceCategoryGroup.GREEN_SPACE, "استخر"),

    INTERNET(ServiceCategoryGroup.COMMUNICATION, "اینترنت"),
    ALARM(ServiceCategoryGroup.COMMUNICATION, "دزدگیر"),
    TV_ANTENNA(ServiceCategoryGroup.COMMUNICATION, "آنتن و تلویزیون"),

    GENERAL(ServiceCategoryGroup.GENERAL, "سایر/عمومی"),
    GUEST(ServiceCategoryGroup.GENERAL, "مهمانان"),
    DELIVERY(ServiceCategoryGroup.GENERAL, "تحویل"),
    DOCUMENTS(ServiceCategoryGroup.GENERAL, "مدارک")
}
