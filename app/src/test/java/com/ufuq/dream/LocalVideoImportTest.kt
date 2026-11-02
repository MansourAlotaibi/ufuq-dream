package com.ufuq.dream

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-xhdpi")
class LocalVideoImportTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Test fun boundedCopyHashesWithoutReadingMetadataOrSavingSettings() {
        val before = SettingsRepository(context).load()
        val target = File.createTempFile("local-import", ".tmp", context.cacheDir)
        try {
            val bytes = ByteArray(2048) { it.toByte() }
            val expected = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(expected, LocalVideoImport.copyBounded(ByteArrayInputStream(bytes), target))
            assertArrayEquals(bytes, target.readBytes()); assertEquals(before, SettingsRepository(context).load())
        } finally { target.delete() }
    }
    @Test fun oversizedAndEmptyStreamsAreRejected() {
        val target = File.createTempFile("local-import", ".tmp", context.cacheDir)
        try {
            try { LocalVideoImport.copyBounded(ByteArrayInputStream(ByteArray(0)), target); fail() } catch (_: IllegalArgumentException) {}
            val endless = object : java.io.InputStream() {
                override fun read() = 0
                override fun read(b: ByteArray, off: Int, len: Int) = len
            }
            try { LocalVideoImport.copyBounded(endless, target); fail() } catch (_: IllegalArgumentException) {}
            assertTrue(target.length() <= LocalVideoImport.MAX_BYTES)
        } finally { target.delete() }
    }
    @Test fun externalPathsAndCanceledPickerNeverMutatePreferences() {
        val repo = SettingsRepository(context); val before = repo.load()
        try { LocalVideoImport.import(context, Uri.parse("file:///not-a-document")); fail() } catch (_: IllegalArgumentException) {}
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        SettingsActivity::class.java.getDeclaredMethod("onActivityResult", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java).apply { isAccessible = true }.invoke(activity, 730, Activity.RESULT_CANCELED, Intent())
        assertEquals(before, repo.load()); assertFalse(activity.draft.dirty)
        controller.pause().stop().destroy()
    }
    @Test fun importRequiresExplicitRightsAcknowledgementAndScopedPicker() {
        val controller = Robolectric.buildActivity(SettingsActivity::class.java).setup()
        val activity = controller.get()
        activity.window.decorView.findViewWithTag<View>("import_video").performClick()
        val dialog = ShadowDialog.getLatestDialog()
        assertNull(shadowOf(activity).nextStartedActivity)
        dialog.findViewById<View>(android.R.id.content).findViewWithTag<View>("choice_1").performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("video/mp4", intent.type)
        assertTrue(intent.categories.contains(Intent.CATEGORY_OPENABLE))
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertFalse(activity.draft.dirty)
        controller.pause().stop().destroy()
    }
}
