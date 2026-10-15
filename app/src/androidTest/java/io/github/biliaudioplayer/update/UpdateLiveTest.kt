package io.github.biliaudioplayer.update

import android.content.pm.PackageInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit opt-in: run after publishing, with instrumentation argument liveUpdate=true. */
@RunWith(AndroidJUnit4::class)
class UpdateLiveTest {
    @Suppress("DEPRECATION")
    @Test fun publicLatestReleaseDownloadsAndMatchesInstalledApplication() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveUpdate") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installed = context.packageManager.getPackageInfo(context.packageName, ApkVerifier.signatureFlags())
        val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        val directory = File(context.cacheDir, "live-update-test-${System.nanoTime()}")
        UpdateRepository().use { repository ->
            try {
                val release = repository.check(0)
                assertNotNull("公开仓库应提供正式 Release 与更新附件", release)
                val latest = release!!
                assertEquals(installedCode, latest.versionCode)
                assertEquals(installed.versionName, latest.versionName)
                assertNull("已安装最新版时应显示已是最新版本", repository.check(installedCode))
                val apk = repository.download(latest, directory)
                assertTrue(apk.isFile && apk.length() > 0)
                val baseline = PackageInfo().apply {
                    packageName = installed.packageName
                    if (Build.VERSION.SDK_INT >= 28) {
                        longVersionCode = installedCode - 1
                        signingInfo = installed.signingInfo
                    } else {
                        versionCode = (installedCode - 1).toInt()
                        signatures = installed.signatures
                    }
                }
                ApkVerifier(context, baseline).verify(apk, latest)
            } finally {
                directory.listFiles()?.forEach { it.delete() }
                directory.delete()
            }
        }
    }
}
