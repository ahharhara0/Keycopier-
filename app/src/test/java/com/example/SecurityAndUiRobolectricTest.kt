package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.KeyCategory
import com.example.domain.model.KeyRecord
import com.example.domain.model.KeyStandards
import com.example.engine.BittingCalculator
import com.example.export.PdfScaleTemplateGenerator
import com.example.export.PngBlueprintGenerator
import com.example.security.KeystoreCryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SecurityAndUiRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun cryptoManager_pinHashingAndEncryptedBackupRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = KeystoreCryptoManager(context)

        crypto.setPin("4829")
        assertTrue(crypto.hasPinConfigured())
        assertTrue(crypto.verifyPin("4829"))
        assertFalse(crypto.verifyPin("0000"))

        val payload = """{"key":"SC1","bitting":"35274"}""".toByteArray()
        val encrypted = crypto.encryptBackupEnvelope(payload, "SecretPass99")
        assertTrue(crypto.isEncryptedBackupEnvelope(encrypted))

        val decrypted = crypto.decryptBackupEnvelope(encrypted, "SecretPass99")
        assertArrayEquals(payload, decrypted)

        val mediaCipher = crypto.encryptMediaBytes(payload)
        val mediaPlain = crypto.decryptMediaBytes(mediaCipher)
        assertArrayEquals(payload, mediaPlain)
    }

    @Test
    fun pdfAndPngGenerators_produceValidBinaryHeaders() {
        val sc1 = KeyStandards.getById("SC1")
        val record = KeyRecord(
            id = 7L,
            name = "Garage Side",
            category = KeyCategory.HOME,
            keyStandardId = "SC1",
            bittingCode = "14263",
            bladeLengthMm = sc1.bladeLengthMm,
            bladeWidthMm = sc1.bladeWidthMm,
            bladeThicknessMm = sc1.bladeThicknessMm,
            pinCount = 5,
            depthsMm = BittingCalculator.depthsFromBittingCode(sc1, "14263", 5),
            spacingsMm = KeyStandards.defaultSpacings(sc1, 5)
        )

        val pdfBytes = PdfScaleTemplateGenerator.generate1To1PdfBytes(record)
        assertTrue(pdfBytes.size > 300)
        assertEquals("%PDF", String(pdfBytes.copyOfRange(0, 4), Charsets.US_ASCII))

        val pngBytes = PngBlueprintGenerator.generateBlueprintPngBytes(record)
        assertTrue(pngBytes.size > 1000)
        assertEquals(0x89.toByte(), pngBytes[0])
        assertEquals('P'.code.toByte(), pngBytes[1])
        assertEquals('N'.code.toByte(), pngBytes[2])
        assertEquals('G'.code.toByte(), pngBytes[3])
    }

    @Test
    fun photoAnalyzer_extractsKeyContourAndGeneratesExact3dStlMesh() {
        val sc1 = KeyStandards.getById("SC1")
        val w = 420
        val h = 220
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h) { android.graphics.Color.WHITE }
        val keyColor = android.graphics.Color.rgb(58, 62, 68)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val inBow = x in 40..135 && y in 58..162
                val cutDepth = when {
                    x in 176..196 -> (18 - kotlin.math.abs(x - 186)).coerceAtLeast(0)
                    x in 220..242 -> (24 - kotlin.math.abs(x - 231)).coerceAtLeast(0)
                    x in 266..286 -> (15 - kotlin.math.abs(x - 276)).coerceAtLeast(0)
                    x in 308..330 -> (22 - kotlin.math.abs(x - 319)).coerceAtLeast(0)
                    else -> 0
                }
                val topBladeY = if (x > 356) 82 + ((x - 356) * 20 / 28) else 82 + cutDepth
                val botBladeY = if (x > 356) 132 - ((x - 356) * 20 / 28) else 132
                val inBlade = x in 136..382 && y in topBladeY..botBladeY
                if (inBow || inBlade) {
                    px[y * w + x] = keyColor
                }
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)

        val extracted = com.example.engine.KeyPhotoAnalyzer.analyzeKeyPhoto(bmp, sc1)
        assertTrue("Expected KeyPhotoAnalyzer to detect synthetic key", extracted.detected)
        assertTrue("Expected extracted custom contour points", extracted.customContourMm.size >= 16)
        assertTrue("Expected detected pin count in 3..8", extracted.pinCount in 3..8)

        val photoRecord = KeyRecord(
            id = 21L,
            name = "Photo Extracted Key",
            category = KeyCategory.HOME,
            keyStandardId = sc1.id,
            bittingCode = "35241",
            bladeLengthMm = extracted.bladeLengthMm,
            bladeWidthMm = extracted.bladeWidthMm,
            bladeThicknessMm = 2.0f,
            pinCount = extracted.pinCount,
            depthsMm = extracted.depthsMm,
            spacingsMm = extracted.spacingsMm,
            isDoubleSided = extracted.isDoubleSided,
            flatWidthsMm = extracted.flatWidthsMm,
            leftSlopesDeg = extracted.leftSlopesDeg,
            rightSlopesDeg = extracted.rightSlopesDeg,
            tipStyle = extracted.tipStyle,
            tipLengthMm = extracted.tipLengthMm,
            bowScale = extracted.bowScale,
            customContourMm = extracted.customContourMm,
            useExactPhotoContour = true
        )
        val mesh = com.example.export.StlMeshGenerator.generateMesh(photoRecord)
        assertTrue("Expected watertight 3D STL triangles from exact photo contour", mesh.triangles.size > 100)
    }

    @Test
    fun photoAnalyzer_handlesTiltedShadowedAndGlareSpottedKeyPhoto() {
        val sc1 = KeyStandards.getById("SC1")
        val w = 460
        val h = 250
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val px = IntArray(w * h)
        val theta = Math.toRadians(6.0)
        val cosT = kotlin.math.cos(theta)
        val sinT = kotlin.math.sin(theta)
        val cx = w * 0.5
        val cy = h * 0.5

        for (y in 0 until h) {
            for (x in 0 until w) {
                // Uneven lighting gradient desk background
                val bgShade = (210 + (x - w * 0.5f) * 0.08f - (y - h * 0.5f) * 0.06f).toInt().coerceIn(170, 245)
                var col = android.graphics.Color.rgb(bgShade, bgShade - 4, bgShade - 10)

                // Soft cast shadow shifted down-right from key
                val sx = (x - 6 - cx) * cosT + (y - 9 - cy) * sinT + cx
                val sy = -(x - 6 - cx) * sinT + (y - 9 - cy) * cosT + cy
                val inShadowBlade = sx in 140.0..388.0 && sy in 96.0..150.0
                if (inShadowBlade) {
                    col = android.graphics.Color.rgb(bgShade - 26, bgShade - 28, bgShade - 32)
                }

                // True rotated dark-brass/steel key silhouette with 4 cuts + interior bright flash glare holes
                val kx = (x - cx) * cosT + (y - cy) * sinT + cx
                val ky = -(x - cx) * sinT + (y - cy) * cosT + cy
                val inBow = kx in 48.0..140.0 && ky in 68.0..176.0
                val kxi = kx.toInt()
                val cutDepth = when {
                    kxi in 182..202 -> (16 - kotlin.math.abs(kxi - 192)).coerceAtLeast(0)
                    kxi in 226..248 -> (23 - kotlin.math.abs(kxi - 237)).coerceAtLeast(0)
                    kxi in 272..292 -> (14 - kotlin.math.abs(kxi - 282)).coerceAtLeast(0)
                    kxi in 314..336 -> (21 - kotlin.math.abs(kxi - 325)).coerceAtLeast(0)
                    else -> 0
                }
                val topBladeY = if (kxi > 362) 92.0 + ((kxi - 362) * 18.0 / 26.0) else 92.0 + cutDepth
                val botBladeY = if (kxi > 362) 142.0 - ((kxi - 362) * 18.0 / 26.0) else 142.0
                val inBlade = kx in 141.0..388.0 && ky in topBladeY..botBladeY
                if (inBow || inBlade) {
                    val isGlareSpot = kotlin.math.hypot(kx - 255.0, ky - 122.0) < 4.5 ||
                        kotlin.math.hypot(kx - 165.0, ky - 120.0) < 4.0
                    col = if (isGlareSpot) {
                        android.graphics.Color.rgb(235, 238, 242) // bright metallic flash glare
                    } else {
                        android.graphics.Color.rgb(52, 56, 64)
                    }
                }
                px[y * w + x] = col
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)

        val extracted = com.example.engine.KeyPhotoAnalyzer.analyzeKeyPhoto(bmp, sc1)
        assertTrue("Expected rotated/shadowed/glare key to be detected", extracted.detected)
        assertEquals(4, extracted.pinCount)
        assertTrue("Expected confident detection >= 75%", extracted.confidencePercent >= 75)
        assertTrue("Expected healed contour >= 20 points", extracted.customContourMm.size >= 20)
    }

    @Test
    fun firstLaunchPinSetupAndNavigationCuj_worksEndToEnd() {
        composeRule.waitForIdle()

        // Step 1: Enter PIN "1234"
        composeRule.onNodeWithTag("pin_digit_1").performClick()
        composeRule.onNodeWithTag("pin_digit_2").performClick()
        composeRule.onNodeWithTag("pin_digit_3").performClick()
        composeRule.onNodeWithTag("pin_digit_4").performClick()
        composeRule.onNodeWithTag("pin_submit_button").performClick()
        composeRule.waitForIdle()

        // Step 2: Confirm PIN "1234"
        composeRule.onNodeWithTag("pin_digit_1").performClick()
        composeRule.onNodeWithTag("pin_digit_2").performClick()
        composeRule.onNodeWithTag("pin_digit_3").performClick()
        composeRule.onNodeWithTag("pin_digit_4").performClick()
        composeRule.onNodeWithTag("pin_submit_button").performClick()
        composeRule.waitForIdle()

        // Unlocked Home Screen should show bottom nav & quick launch button
        composeRule.onNodeWithTag("home_launch_studio_btn").assertIsDisplayed()

        // Open 100-Key Catalog, filter Automotive, select #1 -> loads Longitudinal Glass Match Pad
        composeRule.onNodeWithTag("home_open_catalog_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("catalog_search_input").assertIsDisplayed()
        composeRule.onNodeWithTag("catalog_cat_automotive").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("catalog_cat_all").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("catalog_item_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("longitudinal_pad_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("longitudinal_glass_mode_chip").performClick()
        composeRule.onNodeWithTag("longitudinal_done_button").performClick()
        composeRule.waitForIdle()

        // Navigate across tabs: Studio -> Longitudinal Pad -> Library -> Reports -> Settings
        composeRule.onNodeWithTag("nav_studio").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_bitting_readout").assertIsDisplayed()

        // Toggle double-sided & open longitudinal 1:1 pad
        composeRule.onNodeWithTag("studio_double_sided_chip").performClick()
        composeRule.onNodeWithTag("studio_open_longitudinal_button").performClick()
        composeRule.waitForIdle()

        // Test 0 (Flat) & arrow buttons inside longitudinal pad
        composeRule.onNodeWithTag("longitudinal_pad_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("longitudinal_level_button_0").performClick()
        composeRule.onNodeWithTag("longitudinal_arrow_down").performClick()
        composeRule.onNodeWithTag("longitudinal_done_button").performClick()
        composeRule.waitForIdle()

        // Save the double-sided key to the encrypted vault
        composeRule.onNodeWithTag("studio_save_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_save_name_input").performTextReplacement("Garage Double Key")
        composeRule.onNodeWithTag("studio_confirm_save_button").performClick()
        composeRule.waitForIdle()

        // Verify saved key in Library
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_search_input").assertIsDisplayed()
        composeRule.onNodeWithText("Garage Double Key").assertIsDisplayed()

        // Verify Templates & Export screen shows export cards for the saved key
        composeRule.onNodeWithTag("nav_reports").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("export_stl_card").assertIsDisplayed()

        // Verify Settings & Owner / Open-Source Credits at the bottom
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_change_pin").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_owner_card"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_owner_card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_owner_ar").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_owner_en").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_github_button"))
        composeRule.onNodeWithTag("settings_github_button").performClick()
        composeRule.waitForIdle()
    }
}
