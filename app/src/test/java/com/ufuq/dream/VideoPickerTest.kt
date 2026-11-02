package com.ufuq.dream

import android.content.pm.ActivityInfo
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28])
class VideoPickerTest {
    private fun info(pkg:String)=ActivityInfo().apply {
        packageName=pkg;name="$pkg.Picker";enabled=true;exported=true
    }
    @Test fun excludesMiBoxStubButAcceptsRealPicker() {
        assertFalse(VideoPicker.usable(info("com.google.android.tv.frameworkpackagestubs")))
        assertFalse(VideoPicker.usable(info("com.android.tv.frameworkpackagestubs")))
        assertTrue(VideoPicker.usable(info("example.documents")))
    }
    @Test fun rejectsUnavailableOrProtectedActivities() {
        assertFalse(VideoPicker.usable(info("example.documents").apply{enabled=false}))
        assertFalse(VideoPicker.usable(info("example.documents").apply{exported=false}))
        assertFalse(VideoPicker.usable(info("example.documents").apply{permission="private.permission"}))
    }
    @Test fun fallsBackToRealGetContentWhenDocumentHandlerIsStub() {
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        val pm=org.robolectric.Shadows.shadowOf(context.packageManager)
        fun intent(action:String)=android.content.Intent(action).apply{addCategory(android.content.Intent.CATEGORY_OPENABLE);type="video/mp4";addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        pm.addResolveInfoForIntent(intent(android.content.Intent.ACTION_OPEN_DOCUMENT),android.content.pm.ResolveInfo().apply{activityInfo=info("com.google.android.tv.frameworkpackagestubs")})
        assertNull(VideoPicker.resolve(context))
        pm.addResolveInfoForIntent(intent(android.content.Intent.ACTION_GET_CONTENT),android.content.pm.ResolveInfo().apply{activityInfo=info("example.documents")})
        val result=VideoPicker.resolve(context)!!
        assertEquals(android.content.Intent.ACTION_GET_CONTENT,result.action)
        assertEquals("example.documents",result.component!!.packageName)
    }

}
