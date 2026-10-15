package io.github.biliaudioplayer.update

import android.content.Intent
import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UpdateInstallerTest {
    @Test fun verifiedApkActuallyOpensSystemInstaller() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "updates/system-install-test.apk")
        file.parentFile!!.mkdirs()
        File(context.applicationInfo.sourceDir).copyTo(file, overwrite = true)
        try {
            context.startActivity(UpdateInstaller(context).intent(file, canInstall = true))
            val deadline = SystemClock.uptimeMillis() + 10_000
            var activePackage: String? = null
            do {
                activePackage = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
                if (activePackage?.contains("packageinstaller", ignoreCase = true) == true) break
                SystemClock.sleep(100)
            } while (SystemClock.uptimeMillis() < deadline)
            assertTrue("Expected system package installer, active package was $activePackage",
                activePackage?.contains("packageinstaller", ignoreCase = true) == true)
        } finally {
            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            file.delete()
        }
    }

    @Test fun permissionAndInstallationIntentsUseRealProviderAndSystemHandlers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "updates/installer-test.apk")
        file.parentFile!!.mkdirs()
        file.writeText("provider-read-test")
        try {
            val installer = UpdateInstaller(context)
            val permission = installer.intent(file, canInstall = false)
            assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, permission.action)
            assertEquals("package:${context.packageName}", permission.data.toString())
            assertNotNull(context.packageManager.resolveActivity(permission, 0))

            val install = installer.intent(file, canInstall = true)
            assertEquals(Intent.ACTION_VIEW, install.action)
            assertEquals("application/vnd.android.package-archive", install.type)
            assertEquals("${context.packageName}.updates", install.data!!.authority)
            assertTrue(install.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(install.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertNotNull(context.packageManager.resolveActivity(install, 0))
            assertEquals("provider-read-test", context.contentResolver.openInputStream(install.data!!)!!.bufferedReader().use { it.readText() })
        } finally { file.delete() }
    }
}
