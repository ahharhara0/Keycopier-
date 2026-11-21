package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class KeyCategory(val labelEn: String, val labelAr: String) {
    HOME("Home", "المنزل"),
    OFFICE("Office", "المكتب"),
    VEHICLE("Vehicle", "المركبة"),
    STORAGE("Storage", "المخزن"),
    PERSONAL("Personal", "شخصي"),
    OTHER("Other", "أخرى")
}

@Serializable
enum class AttachmentType {
    ORIGINAL_PHOTO,
    CALIBRATED_IMAGE,
    STL_MODEL,
    SVG_TEMPLATE,
    DXF_TEMPLATE,
    PDF_TEMPLATE
}

@Serializable
enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    ARABIC("ar")
}

@Serializable
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Serializable
enum class MeasurementUnit {
    MM,
    MIL
}

enum class AutoLockTimeout(val seconds: Int) {
    IMMEDIATE(0),
    THIRTY_SECONDS(30),
    ONE_MINUTE(60),
    FIVE_MINUTES(300),
    NEVER(-1);

    companion object {
        fun fromSeconds(seconds: Int): AutoLockTimeout =
            entries.firstOrNull { it.seconds == seconds } ?: THIRTY_SECONDS
    }
}

@Serializable
data class KeyStandardSpec(
    val id: String,
    val displayName: String,
    val manufacturer: String,
    val pinCount: Int,
    val shoulderToFirstPinMm: Float,
    val pinSpacingMm: Float,
    val bladeLengthMm: Float,
    val bladeWidthMm: Float,
    val bladeThicknessMm: Float,
    val minCode: Int,
    val maxCode: Int,
    val depthTableMm: List<Float>, // cut depth from top edge of blade (or root height from spine = bladeWidth - depth)
    val grooveOffsetsMm: List<Float> = listOf(2.4f, 5.4f) // longitudinal warding groove centers from spine
) {
    fun cutDepthForCode(code: Int): Float {
        val idx = (code - minCode).coerceIn(0, depthTableMm.lastIndex)
        return depthTableMm[idx]
    }

    fun nearestCodeForCutDepth(cutDepthMm: Float): Pair<Int, Float> {
        var bestCode = minCode
        var minDiff = Float.MAX_VALUE
        depthTableMm.forEachIndexed { index, specDepth ->
            val diff = kotlin.math.abs(specDepth - cutDepthMm)
            if (diff < minDiff) {
                minDiff = diff
                bestCode = minCode + index
            }
        }
        return bestCode to (cutDepthMm - cutDepthForCode(bestCode))
    }
}

@Serializable
enum class KeyTipStyle(val labelEn: String, val labelAr: String) {
    STANDARD_TAPER("Standard", "قياسي"),
    SHARP_POINT("Sharp Point", "مدبب حاد"),
    CHISEL_FLAT("Chisel Flat", "مشطوف"),
    ROUNDED("Rounded", "دائري"),
    SLANT_TOP("Slant Top", "مائل علوي")
}

@Serializable
enum class KeyBowStyle(val labelEn: String, val labelAr: String) {
    CLASSIC_OVAL("Classic Oval", "بيضاوي قياسي"),
    HEX_SCHLAGE("Hexagonal", "سداسي"),
    SQUARE_HEAVY("Square Heavy", "مربع ثقيل"),
    TULIP_CROWN("Tulip / Crown", "تاجي مزخرف"),
    AUTO_FOB("Automotive", "مفتاح سيارة"),
    SLIM_PADLOCK("Slim Padlock", "قفل / خزنة"),
    ROUND_COIN("Round Coin", "دائري كامل")
}

enum class CatalogCategory(val labelEn: String, val labelAr: String) {
    ALL("All (1,000)", "الكل (1000)"),
    RESIDENTIAL("Doors & Houses", "منازل وأبواب"),
    AUTOMOTIVE("Cars & Bikes", "سيارات ودراجات"),
    PADLOCK_CABINET("Padlocks & Safes", "أقفال وخزائن"),
    SPECIALTY_HIGH_SEC("Double & Special", "أبو جهتين وخاصة")
}

data class KeyCatalogPreset(
    val id: String,
    val number: Int,
    val nameEn: String,
    val nameAr: String,
    val codeTag: String,
    val catalogCategory: CatalogCategory,
    val keyCategory: KeyCategory,
    val standardId: String,
    val pinCount: Int,
    val bladeLengthMm: Float,
    val bladeWidthMm: Float,
    val bladeThicknessMm: Float,
    val spacingsMm: List<Float>,
    val depthsMm: List<Float>,
    val flatWidthsMm: List<Float>,
    val leftSlopesDeg: List<Float>,
    val rightSlopesDeg: List<Float>,
    val isDoubleSided: Boolean,
    val bowStyle: KeyBowStyle,
    val bowScale: Float,
    val tipStyle: KeyTipStyle,
    val tipLengthMm: Float,
    val tipOffsetMm: Float = 0f,
    val bittingCode: String = ""
) {
    fun toPreviewRecord(isArabic: Boolean): KeyRecord = KeyRecord(
        id = -number.toLong(),
        name = if (isArabic) nameAr else nameEn,
        category = keyCategory,
        keyStandardId = standardId,
        bittingCode = bittingCode.ifEmpty {
            depthsMm.joinToString("") {
                ((it / (bladeWidthMm * 0.45f)).coerceIn(0f, 1f) * 9f).toInt().coerceIn(0, 9).toString()
            }
        },
        bladeLengthMm = bladeLengthMm,
        bladeWidthMm = bladeWidthMm,
        bladeThicknessMm = bladeThicknessMm,
        pinCount = pinCount,
        depthsMm = depthsMm,
        spacingsMm = spacingsMm,
        isDoubleSided = isDoubleSided,
        flatWidthsMm = flatWidthsMm,
        leftSlopesDeg = leftSlopesDeg,
        rightSlopesDeg = rightSlopesDeg,
        tipStyle = tipStyle,
        tipLengthMm = tipLengthMm,
        tipOffsetMm = tipOffsetMm,
        bowStyle = bowStyle,
        bowScale = bowScale
    )
}

@Serializable
data class KeyRecord(
    val id: Long = 0L,
    val name: String,
    val category: KeyCategory = KeyCategory.HOME,
    val keyStandardId: String = "SC1",
    val bittingCode: String,
    val bladeLengthMm: Float,
    val bladeWidthMm: Float,
    val bladeThicknessMm: Float,
    val pinCount: Int,
    val depthsMm: List<Float>,
    val spacingsMm: List<Float>,
    val isDoubleSided: Boolean = false,
    val flatWidthsMm: List<Float> = emptyList(),
    val leftSlopesDeg: List<Float> = emptyList(),
    val rightSlopesDeg: List<Float> = emptyList(),
    val tipStyle: KeyTipStyle = KeyTipStyle.STANDARD_TAPER,
    val tipLengthMm: Float = 2.6f,
    val tipOffsetMm: Float = 0f,
    val bowStyle: KeyBowStyle = KeyBowStyle.CLASSIC_OVAL,
    val bowScale: Float = 1.0f,
    val customContourMm: List<Point2D> = emptyList(),
    val useExactPhotoContour: Boolean = false,
    val notes: String = "",
    val tags: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class KeyAttachment(
    val id: Long = 0L,
    val keyRecordId: Long,
    val localPath: String,
    val fileType: AttachmentType,
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class AppSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val theme: AppThemeMode = AppThemeMode.SYSTEM,
    val preferredUnit: MeasurementUnit = MeasurementUnit.MM,
    val biometricEnabled: Boolean = false,
    val autoLockSeconds: Int = 30,
    val screenDpiOverride: Float = 0f, // 0 = use system DisplayMetrics.xdpi
    val onboardingCompleted: Boolean = false,
    val templatesGeneratedCount: Int = 0
)

@Serializable
data class VaultBackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val app: String = "KeyCopier Pro",
    val records: List<KeyRecord>
)

@Serializable
data class Point2D(
    val x: Float,
    val y: Float
)
