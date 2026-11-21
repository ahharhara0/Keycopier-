package com.example.domain.model

object KeyStandards {

    // Real-world locksmith depth & spacing specs (in millimeters)
    // cutDepthMm = cut depth measured down from the top uncut edge of the blade.
    val ALL: List<KeyStandardSpec> = listOf(
        KeyStandardSpec(
            id = "SC1",
            displayName = "Schlage SC1 (5-Pin)",
            manufacturer = "Schlage",
            pinCount = 5,
            shoulderToFirstPinMm = 5.87f,
            pinSpacingMm = 3.96f,
            bladeLengthMm = 28.50f,
            bladeWidthMm = 8.46f,
            bladeThicknessMm = 2.03f,
            minCode = 0,
            maxCode = 9,
            depthTableMm = listOf(0.00f, 0.38f, 0.76f, 1.14f, 1.52f, 1.91f, 2.29f, 2.67f, 3.05f, 3.43f),
            grooveOffsetsMm = listOf(2.6f, 5.3f)
        ),
        KeyStandardSpec(
            id = "SC4",
            displayName = "Schlage SC4 (6-Pin)",
            manufacturer = "Schlage",
            pinCount = 6,
            shoulderToFirstPinMm = 5.87f,
            pinSpacingMm = 3.96f,
            bladeLengthMm = 32.50f,
            bladeWidthMm = 8.46f,
            bladeThicknessMm = 2.03f,
            minCode = 0,
            maxCode = 9,
            depthTableMm = listOf(0.00f, 0.38f, 0.76f, 1.14f, 1.52f, 1.91f, 2.29f, 2.67f, 3.05f, 3.43f),
            grooveOffsetsMm = listOf(2.6f, 5.3f)
        ),
        KeyStandardSpec(
            id = "KW1",
            displayName = "Kwikset KW1 (5-Pin)",
            manufacturer = "Kwikset",
            pinCount = 5,
            shoulderToFirstPinMm = 6.27f,
            pinSpacingMm = 3.81f,
            bladeLengthMm = 28.00f,
            bladeWidthMm = 8.38f,
            bladeThicknessMm = 2.03f,
            minCode = 1,
            maxCode = 7,
            depthTableMm = listOf(0.66f, 1.24f, 1.83f, 2.41f, 3.00f, 3.58f, 4.17f),
            grooveOffsetsMm = listOf(2.4f, 5.1f)
        ),
        KeyStandardSpec(
            id = "KW10",
            displayName = "Kwikset Titan KW10 (6-Pin)",
            manufacturer = "Kwikset",
            pinCount = 6,
            shoulderToFirstPinMm = 6.27f,
            pinSpacingMm = 3.81f,
            bladeLengthMm = 31.80f,
            bladeWidthMm = 8.38f,
            bladeThicknessMm = 2.03f,
            minCode = 1,
            maxCode = 7,
            depthTableMm = listOf(0.66f, 1.24f, 1.83f, 2.41f, 3.00f, 3.58f, 4.17f),
            grooveOffsetsMm = listOf(2.4f, 5.1f)
        ),
        KeyStandardSpec(
            id = "Y1",
            displayName = "Yale Y1 (5-Pin)",
            manufacturer = "Yale",
            pinCount = 5,
            shoulderToFirstPinMm = 5.08f,
            pinSpacingMm = 4.19f,
            bladeLengthMm = 28.20f,
            bladeWidthMm = 8.64f,
            bladeThicknessMm = 1.98f,
            minCode = 0,
            maxCode = 9,
            depthTableMm = listOf(0.51f, 0.99f, 1.47f, 1.96f, 2.44f, 2.92f, 3.40f, 3.89f, 4.37f, 4.85f),
            grooveOffsetsMm = listOf(2.8f, 5.5f)
        ),
        KeyStandardSpec(
            id = "WR5",
            displayName = "Weiser WR5 (5-Pin)",
            manufacturer = "Weiser",
            pinCount = 5,
            shoulderToFirstPinMm = 5.99f,
            pinSpacingMm = 3.96f,
            bladeLengthMm = 28.40f,
            bladeWidthMm = 8.43f,
            bladeThicknessMm = 2.03f,
            minCode = 0,
            maxCode = 9,
            depthTableMm = listOf(0.46f, 0.91f, 1.37f, 1.83f, 2.29f, 2.74f, 3.20f, 3.66f, 4.11f, 4.57f),
            grooveOffsetsMm = listOf(2.5f, 5.2f)
        ),
        KeyStandardSpec(
            id = "AR1",
            displayName = "Arrow AR1 (5-Pin)",
            manufacturer = "Arrow",
            pinCount = 5,
            shoulderToFirstPinMm = 6.73f,
            pinSpacingMm = 3.94f,
            bladeLengthMm = 29.00f,
            bladeWidthMm = 8.18f,
            bladeThicknessMm = 2.00f,
            minCode = 0,
            maxCode = 9,
            depthTableMm = listOf(0.36f, 0.71f, 1.07f, 1.42f, 1.78f, 2.13f, 2.49f, 2.84f, 3.20f, 3.56f),
            grooveOffsetsMm = listOf(2.4f, 5.0f)
        ),
        KeyStandardSpec(
            id = "M1",
            displayName = "Master Lock M1 (4-Pin)",
            manufacturer = "Master Lock",
            pinCount = 4,
            shoulderToFirstPinMm = 4.65f,
            pinSpacingMm = 3.66f,
            bladeLengthMm = 22.00f,
            bladeWidthMm = 6.86f,
            bladeThicknessMm = 1.88f,
            minCode = 0,
            maxCode = 7,
            depthTableMm = listOf(0.38f, 0.76f, 1.14f, 1.52f, 1.91f, 2.29f, 2.67f, 3.05f),
            grooveOffsetsMm = listOf(2.1f, 4.3f)
        ),
        KeyStandardSpec(
            id = "SRG6",
            displayName = "Sargent LA/LB/LC (6-Pin)",
            manufacturer = "Sargent",
            pinCount = 6,
            shoulderToFirstPinMm = 7.14f,
            pinSpacingMm = 3.96f,
            bladeLengthMm = 32.80f,
            bladeWidthMm = 8.43f,
            bladeThicknessMm = 2.03f,
            minCode = 1,
            maxCode = 10,
            depthTableMm = listOf(0.51f, 1.02f, 1.52f, 2.03f, 2.54f, 3.05f, 3.56f, 4.06f, 4.57f, 5.08f),
            grooveOffsetsMm = listOf(2.5f, 5.4f)
        ),
        KeyStandardSpec(
            id = "CR6",
            displayName = "Corbin Russwin Sys70 (6-Pin)",
            manufacturer = "Corbin Russwin",
            pinCount = 6,
            shoulderToFirstPinMm = 6.35f,
            pinSpacingMm = 3.49f,
            bladeLengthMm = 30.50f,
            bladeWidthMm = 8.35f,
            bladeThicknessMm = 2.03f,
            minCode = 1,
            maxCode = 6,
            depthTableMm = listOf(0.46f, 1.07f, 1.68f, 2.29f, 2.90f, 3.51f),
            grooveOffsetsMm = listOf(2.4f, 5.2f)
        ),
        KeyStandardSpec(
            id = "BEST7",
            displayName = "Best A2 SFIC (7-Pin)",
            manufacturer = "Best Access",
            pinCount = 7,
            shoulderToFirstPinMm = 5.87f,
            pinSpacingMm = 3.81f,
            bladeLengthMm = 33.40f,
            bladeWidthMm = 8.15f,
            bladeThicknessMm = 1.98f,
            minCode = 0,
            maxCode = 9,
            depthTableMm = listOf(0.00f, 0.32f, 0.63f, 0.95f, 1.26f, 1.58f, 1.89f, 2.21f, 2.52f, 2.84f),
            grooveOffsetsMm = listOf(2.3f, 5.1f)
        ),
        KeyStandardSpec(
            id = "AM5",
            displayName = "American Lock 1100 (5-Pin)",
            manufacturer = "American Lock",
            pinCount = 5,
            shoulderToFirstPinMm = 5.54f,
            pinSpacingMm = 3.96f,
            bladeLengthMm = 26.80f,
            bladeWidthMm = 7.80f,
            bladeThicknessMm = 1.93f,
            minCode = 1,
            maxCode = 8,
            depthTableMm = listOf(0.38f, 0.94f, 1.50f, 2.06f, 2.62f, 3.18f, 3.73f, 4.29f),
            grooveOffsetsMm = listOf(2.2f, 4.9f)
        ),
        KeyStandardSpec(
            id = "AB5",
            displayName = "ABUS C83 Euro (5-Pin)",
            manufacturer = "ABUS",
            pinCount = 5,
            shoulderToFirstPinMm = 4.50f,
            pinSpacingMm = 3.90f,
            bladeLengthMm = 27.60f,
            bladeWidthMm = 8.20f,
            bladeThicknessMm = 2.10f,
            minCode = 0,
            maxCode = 7,
            depthTableMm = listOf(0.00f, 0.48f, 0.96f, 1.44f, 1.92f, 2.40f, 2.88f, 3.36f),
            grooveOffsetsMm = listOf(2.4f, 5.1f)
        ),
        KeyStandardSpec(
            id = "NAT5",
            displayName = "CompX National Cabinet (5-Pin)",
            manufacturer = "National",
            pinCount = 5,
            shoulderToFirstPinMm = 5.33f,
            pinSpacingMm = 3.56f,
            bladeLengthMm = 24.50f,
            bladeWidthMm = 6.75f,
            bladeThicknessMm = 1.78f,
            minCode = 0,
            maxCode = 7,
            depthTableMm = listOf(0.00f, 0.46f, 0.91f, 1.37f, 1.83f, 2.29f, 2.74f, 3.20f),
            grooveOffsetsMm = listOf(2.0f, 4.2f)
        ),
        KeyStandardSpec(
            id = "TOY43",
            displayName = "Toyota / Lexus TOY43 (8-Cut)",
            manufacturer = "Toyota",
            pinCount = 8,
            shoulderToFirstPinMm = 5.20f,
            pinSpacingMm = 3.20f,
            bladeLengthMm = 34.00f,
            bladeWidthMm = 8.00f,
            bladeThicknessMm = 2.25f,
            minCode = 1,
            maxCode = 4,
            depthTableMm = listOf(0.55f, 1.13f, 1.71f, 2.29f),
            grooveOffsetsMm = listOf(2.4f, 5.2f)
        ),
        KeyStandardSpec(
            id = "HU66",
            displayName = "VAG VW/Audi HU66 (8-Cut)",
            manufacturer = "Volkswagen / Audi",
            pinCount = 8,
            shoulderToFirstPinMm = 4.90f,
            pinSpacingMm = 2.95f,
            bladeLengthMm = 32.60f,
            bladeWidthMm = 7.60f,
            bladeThicknessMm = 2.80f,
            minCode = 1,
            maxCode = 4,
            depthTableMm = listOf(0.48f, 1.00f, 1.52f, 2.04f),
            grooveOffsetsMm = listOf(2.3f, 4.9f)
        ),
        KeyStandardSpec(
            id = "CUSTOM",
            displayName = "Custom Metric (Freeform)",
            manufacturer = "Generic",
            pinCount = 5,
            shoulderToFirstPinMm = 5.50f,
            pinSpacingMm = 4.00f,
            bladeLengthMm = 28.00f,
            bladeWidthMm = 8.50f,
            bladeThicknessMm = 2.00f,
            minCode = 0,
            maxCode = 9,
            depthTableMm = listOf(0.00f, 0.45f, 0.90f, 1.35f, 1.80f, 2.25f, 2.70f, 3.15f, 3.60f, 4.05f),
            grooveOffsetsMm = listOf(2.5f, 5.5f)
        )
    )

    fun getById(id: String): KeyStandardSpec =
        ALL.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ALL.first()

    fun defaultSpacings(spec: KeyStandardSpec, pinCount: Int = spec.pinCount): List<Float> =
        List(pinCount) { index ->
            spec.shoulderToFirstPinMm + index * spec.pinSpacingMm
        }

    fun defaultDepths(spec: KeyStandardSpec, pinCount: Int = spec.pinCount): List<Float> {
        val samplePattern = listOf(3, 5, 2, 6, 4, 3, 5, 2)
        return List(pinCount) { index ->
            val code = samplePattern[index % samplePattern.size]
                .coerceIn(spec.minCode, spec.maxCode)
            spec.cutDepthForCode(code)
        }
    }
}
