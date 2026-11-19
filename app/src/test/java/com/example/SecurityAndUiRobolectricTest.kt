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
