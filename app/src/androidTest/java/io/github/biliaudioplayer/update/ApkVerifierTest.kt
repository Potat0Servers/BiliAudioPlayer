package io.github.biliaudioplayer.update

import android.content.pm.PackageInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ApkVerifierTest {
    @Suppress("DEPRECATION")
    @Test fun actualSignedApkPassesAndWrongIdentityVersionOrSignerIsRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actual = context.packageManager.getPackageInfo(context.packageName, ApkVerifier.signatureFlags())
        val apk = File(context.applicationInfo.sourceDir)
        val code = if (Build.VERSION.SDK_INT >= 28) actual.longVersionCode else actual.versionCode.toLong()
        val release = UpdateRelease(actual.versionName!!, code, "test.apk", "unused-here", "unused-here")
        fun baseline(packageName: String = actual.packageName, baselineCode: Long = code - 1) = PackageInfo().apply {
            this.packageName = packageName
            if (Build.VERSION.SDK_INT >= 28) {
                longVersionCode = baselineCode
                signingInfo = actual.signingInfo
            } else {
                versionCode = baselineCode.toInt()
                signatures = actual.signatures
            }
        }
        ApkVerifier(context, baseline()).verify(apk, release)
        rejected("包名") { ApkVerifier(context, baseline(packageName = "other.application")).verify(apk, release) }
        rejected("版本") { ApkVerifier(context, baseline()).verify(apk, release.copy(versionCode = code + 1)) }
        rejected("版本") { ApkVerifier(context, baseline()).verify(apk, release.copy(versionName = "invalid-version")) }
        rejected("版本") { ApkVerifier(context, baseline(baselineCode = code)).verify(apk, release) }
        rejected("签名") { ApkVerifier(context, baseline(), expectedSignatures = setOf("different-signing-certificate")).verify(apk, release) }
    }

    private fun rejected(message: String, block: () -> Unit) {
        try {
            block()
            fail("应拒绝不匹配的更新包")
        } catch (error: IOException) { assertTrue(error.message.orEmpty().contains(message)) }
    }
}
