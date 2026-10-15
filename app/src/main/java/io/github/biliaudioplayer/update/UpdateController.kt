package io.github.biliaudioplayer.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class UpdatePhase { IDLE, CHECKING, LATEST, AVAILABLE, DOWNLOADING, READY, ERROR }

data class UpdateUiState(
    val currentVersion: String = "",
    val currentVersionCode: Long = 0,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: UpdateRelease? = null,
    val progress: Float? = null,
    val message: String? = null,
)

class UpdateController(context: Context) {
    private val app = context.applicationContext
    private val repository = UpdateRepository()
    private val installer = UpdateInstaller(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installed = packageInfo(app.packageName)
    private val verifier = ApkVerifier(app, installed)
    private val mutableState = MutableStateFlow(UpdateUiState(installed.versionName.orEmpty(), versionCode(installed)))
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var downloaded: File? = null

    fun check() {
        if (busy()) return
        mutableState.update { it.copy(phase = UpdatePhase.CHECKING, message = null, release = null, progress = null) }
        scope.launch {
            try {
                val release = repository.check(versionCode(installed))
                mutableState.update { it.copy(phase = if (release == null) UpdatePhase.LATEST else UpdatePhase.AVAILABLE, release = release, message = if (release == null) "已是最新版本" else null) }
            } catch (error: Exception) { fail(error) }
        }
    }

    fun download() {
        if (busy()) return
        val release = state.value.release ?: return
        mutableState.update { it.copy(phase = UpdatePhase.DOWNLOADING, progress = null, message = null) }
        scope.launch {
            var file: File? = null
            try {
                file = repository.download(release, File(app.cacheDir, "updates")) { progress -> mutableState.update { it.copy(progress = progress) } }
                verifier.verify(file, release)
                downloaded = file
                mutableState.update { it.copy(phase = UpdatePhase.READY, progress = 1f, message = "下载完成，可安装更新") }
                withContext(Dispatchers.Main) { install() }
            } catch (error: Exception) {
                file?.delete()
                downloaded = null
                fail(error)
            }
        }
    }

    /** Call from the main thread. Returning from the permission screen leaves an explicit retry action. */
    fun install() {
        val file = downloaded
        if (file == null || !file.isFile) {
            mutableState.update { it.copy(phase = UpdatePhase.ERROR, message = "更新包已清理，请重新下载") }
            return
        }
        try {
            val intent = installer.intent(file)
            if (intent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES) {
                app.startActivity(intent)
                mutableState.update { it.copy(message = "请允许安装此来源的应用，返回后点击“安装更新”") }
                return
            }
            app.startActivity(intent)
            mutableState.update { it.copy(message = "请在系统页面确认安装；取消后可再次点击安装") }
        } catch (error: Exception) {
            mutableState.update { it.copy(phase = UpdatePhase.READY, message = "无法打开系统安装页面：${error.localizedMessage.orEmpty()}") }
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(name: String): PackageInfo = app.packageManager.getPackageInfo(name, signatureFlags())

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    private fun busy() = state.value.phase == UpdatePhase.CHECKING || state.value.phase == UpdatePhase.DOWNLOADING

    private fun fail(error: Exception) {
        if (error is CancellationException) throw error
        mutableState.update { it.copy(phase = UpdatePhase.ERROR, message = error.localizedMessage?.takeIf(String::isNotBlank) ?: "网络连接失败，请重试") }
    }

    fun close() { scope.cancel(); repository.close() }
}
