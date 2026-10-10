package app.vela.core.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supported road report categories for Laze.
 */
@Serializable
enum class RoadReportType(val apiValue: String, val titleAr: String, val titleEn: String) {
    @SerialName("accident")
    ACCIDENT("accident", "حادث مروري", "Crash"),

    @SerialName("traffic")
    TRAFFIC("traffic", "ازدحام مروري", "Traffic Jam"),

    @SerialName("closure")
    CLOSURE("closure", "إغلاق طريق", "Road Closed"),

    @SerialName("roadwork")
    ROADWORK("roadwork", "أعمال صيانة", "Construction"),

    @SerialName("hazard")
    HAZARD("hazard", "خطر على الطريق", "Hazard"),

    @SerialName("breakdown")
    BREAKDOWN("breakdown", "مركبة متعطلة", "Disabled Vehicle"),

    @SerialName("flood")
    FLOOD("flood", "طريق مغمور بالمياه", "Flooding"),

    @SerialName("camera")
    SPEED_CAMERA("camera", "كاميرا سرعة", "Speed Camera"),

    @SerialName("checkpoint")
    CHECKPOINT("checkpoint", "سيطرة / نقطة تفتيش", "Checkpoint"),

    @SerialName("speed_bump")
    SPEED_BUMP("speed_bump", "مطب صناعي", "Speed Bump"),

    @SerialName("pothole")
    POTHOLE("pothole", "حفرة أو تخسف", "Pothole");

    companion object {
        fun fromApi(value: String): RoadReportType = entries.firstOrNull {
            it.apiValue.equals(value, ignoreCase = true)
        } ?: HAZARD
    }
}

@Serializable
enum class RoadReportSeverity(val apiValue: String) {
    @SerialName("low")
    LOW("low"),

    @SerialName("moderate")
    MODERATE("moderate"),

    @SerialName("high")
    HIGH("high"),

    @SerialName("severe")
    SEVERE("severe");

    companion object {
        fun fromApi(value: String): RoadReportSeverity = entries.firstOrNull {
            it.apiValue.equals(value, ignoreCase = true)
        } ?: MODERATE
    }
}

@Serializable
enum class RoadReportStatus(val apiValue: String) {
    @SerialName("active")
    ACTIVE("active"),

    @SerialName("cleared")
    CLEARED("cleared"),

    @SerialName("expired")
    EXPIRED("expired");

    companion object {
        fun fromApi(value: String): RoadReportStatus = entries.firstOrNull {
            it.apiValue.equals(value, ignoreCase = true)
        } ?: ACTIVE
    }
}

/**
 * Represents a community road report in Laze.
 */
@Serializable
data class RoadReport(
    @SerialName("id")
    val id: String = "",

    @SerialName("user_id")
    val userId: String? = null,

    @SerialName("type")
    val type: RoadReportType,

    @SerialName("latitude")
    val latitude: Double,

    @SerialName("longitude")
    val longitude: Double,

    @SerialName("bearing")
    val bearing: Double? = null,

    @SerialName("severity")
    val severity: RoadReportSeverity = RoadReportSeverity.MODERATE,

    @SerialName("description")
    val description: String? = null,

    @SerialName("road_name")
    val roadName: String? = null,

    @SerialName("status")
    val status: RoadReportStatus = RoadReportStatus.ACTIVE,

    @SerialName("confirmations_count")
    val confirmationsCount: Int = 1,

    @SerialName("rejections_count")
    val rejectionsCount: Int = 0,

    @SerialName("reliability_score")
    val reliabilityScore: Double = 1.0,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("expires_at")
    val expiresAt: String? = null,
) {
    /** True if the report is active and reliable */
    val isReliable: Boolean
        get() = status == RoadReportStatus.ACTIVE && reliabilityScore >= 0.5 && rejectionsCount < 3

    /** Distance helper in meters to another coordinate */
    fun distanceMetersTo(lat: Double, lng: Double): Double {
        val latRad1 = Math.toRadians(latitude)
        val latRad2 = Math.toRadians(lat)
        val deltaLat = Math.toRadians(lat - latitude)
        val deltaLng = Math.toRadians(lng - longitude)
        val a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
            Math.cos(latRad1) * Math.cos(latRad2) *
            Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return 6371000.0 * c
    }
}
