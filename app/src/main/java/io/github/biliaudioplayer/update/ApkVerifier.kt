package io.github.biliaudioplayer.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException

internal class ApkVerifier(
    private val context: Context,
    private val installed: PackageInfo,
    private val expectedSignatures: Set<String> = signatures(installed),
) {
    fun verify(file: File, release: UpdateRelease) {
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, signatureFlags()) ?: throw IOException("更新文件不是有效 APK")
        if (archive.packageName != installed.packageName) throw IOException("更新包的应用包名不匹配")
        if (versionCode(archive) != release.versionCode || versionCode(archive) <= versionCode(installed) || archive.versionName != release.versionName) {
            throw IOException("更新包版本与发布说明不匹配")
        }
        if (expectedSignatures.isEmpty() || expectedSignatures != signatures(archive)) throw IOException("更新包签名不匹配，无法覆盖安装")
    }

    companion object {
        @Suppress("DEPRECATION")
        fun signatureFlags(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

        @Suppress("DEPRECATION")
        private fun signatures(info: PackageInfo): Set<String> =
            (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
                ?.map { it.toCharsString() }?.toSet().orEmpty()

        @Suppress("DEPRECATION")
        private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
}
