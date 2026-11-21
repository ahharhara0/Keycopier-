package com.example.domain.model

/**
 * 1,000 real-world parametric key blank & OEM factory cut profiles across 248 distinct
 * lock/blank families (Silca, Ilco, JMA, HPC CodeMax, LockWiki, and KeyCAD specifications).
 *
 * Categories (250 presets each):
 *  - 1..250: Residential & Commercial Cylinders (Schlage, Kwikset, Weiser, Yale, Sargent, Corbin, Euro...)
 *  - 251..500: Automotive, Motorcycles & Fleet (Toyota, Honda, Nissan, Ford, GM, Chrysler, VW, BMW, MBZ...)
 *  - 501..750: Padlocks, Cabinets, Mailboxes & Safes (Master Lock, American Lock, ABUS, National, Chicago...)
 *  - 751..1000: SFIC, Medeco, Mul-T-Lock, Dimple, Cruciform, Double-Sided & Universal Reference Blanks
 */
object KeyBlankCatalog {

    val ALL_1000: List<KeyCatalogPreset> by lazy {
        CatalogResidentialBlanks.PRESETS +
            CatalogAutomotiveBlanks.PRESETS +
            CatalogPadlockCabinetBlanks.PRESETS +
            CatalogSpecialtyBlanks.PRESETS
    }

    fun filter(
        category: CatalogCategory = CatalogCategory.ALL,
        pinCountFilter: Int? = null, // null = All, 7 = 7 pins, 8 = 8+ pins (includes 10-cut auto)
        query: String = "",
        uncutOnly: Boolean = false,
        doubleSidedOnly: Boolean = false
    ): List<KeyCatalogPreset> {
        val q = query.trim().lowercase()
        val numericQuery = q.removePrefix("#").toIntOrNull()
        return ALL_1000.filter { preset ->
            val catOk = category == CatalogCategory.ALL || preset.catalogCategory == category
            val pinOk = when (pinCountFilter) {
                null -> true
                8 -> preset.pinCount >= 8
                else -> preset.pinCount == pinCountFilter
            }
            val uncutOk = !uncutOnly || preset.depthsMm.all { it <= 0.01f }
            val dblOk = !doubleSidedOnly || preset.isDoubleSided
            val queryOk = q.isEmpty() ||
                (numericQuery != null && preset.number == numericQuery) ||
                preset.nameEn.lowercase().contains(q) ||
                preset.nameAr.lowercase().contains(q) ||
                preset.codeTag.lowercase().contains(q) ||
                preset.standardId.lowercase().contains(q) ||
                preset.bittingCode.lowercase().contains(q) ||
                preset.bowStyle.labelEn.lowercase().contains(q) ||
                preset.bowStyle.labelAr.lowercase().contains(q)
            catOk && pinOk && uncutOk && dblOk && queryOk
        }
    }
}
