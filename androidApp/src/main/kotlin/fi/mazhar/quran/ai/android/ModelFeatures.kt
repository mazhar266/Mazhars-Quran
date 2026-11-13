package fi.mazhar.quran.ai.android

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import fi.mazhar.quran.ai.R
import fi.mazhar.quran.ai.features.Feature
import fi.mazhar.quran.ai.features.FeatureController
import fi.mazhar.quran.ai.features.FeatureStatus
import fi.mazhar.quran.ai.llm.LlamaEngine
import fi.mazhar.quran.ai.stt.WhisperModel
import fi.mazhar.quran.ai.voice.LlmEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The optional AI and Voice features on Android: remembers which are switched
 * on, downloads their model files with the system [DownloadManager] (so a
 * download survives the app being closed), verifies them against a pinned
 * SHA-256, and loads/unloads the native models.
 *
 * One instance per process: the models must outlive activity re-creation.
 */
class ModelFeatures private constructor(private val context: Context) : FeatureController {

    /** A model file pinned to an exact repository revision and checksum. */
    private class Spec(
        val fileName: String,
        val url: String,
        val bytes: Long,
        val sha256: String,
        val sizeLabel: String,
        /** Large enough that the user is asked before it goes over mobile data. */
        val askOnMobileData: Boolean,
    )

    private val specs = mapOf(
        Feature.AI to Spec(
            fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/" +
                "91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            bytes = 1_117_320_736,
            sha256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
            sizeLabel = "1.1 GB",
            askOnMobileData = true,
        ),
        Feature.VOICE to Spec(
            fileName = "ggml-base-q5_1.bin",
            url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/" +
                "5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base-q5_1.bin",
            bytes = 59_707_625,
            sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
            sizeLabel = "57 MB",
            askOnMobileData = false,
        ),
    )

    private val prefs = context.getSharedPreferences("features", Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobs = HashMap<Feature, Job>()

    @Volatile private var engine: LlamaEngine? = null
    @Volatile private var whisper: WhisperModel? = null

    private val _status = MutableStateFlow(
        Feature.entries.associateWith<Feature, FeatureStatus> { FeatureStatus.Off(file(it)?.exists() == true) },
    )
    override val status: StateFlow<Map<Feature, FeatureStatus>> = _status

    /** The language model when AI commands are on and loaded; otherwise every call fails fast. */
    val llm: LlmEngine = object : LlmEngine {
        override suspend fun complete(system: String, user: String, grammar: String): String =
            (engine ?: error("AI commands are off or still loading")).complete(system, user, grammar)
    }

    /** The speech model when voice input is on and loaded. */
    fun speechModel(): WhisperModel? = whisper

    init {
        for (feature in Feature.entries) if (prefs.getBoolean(enabledKey(feature), false)) start(feature)
    }

    override fun downloadSize(feature: Feature): String = specs.getValue(feature).sizeLabel

    override fun wouldUseMobileData(feature: Feature): Boolean =
        specs.getValue(feature).askOnMobileData && file(feature)?.exists() != true &&
            connectivity.isActiveNetworkMetered

    @Synchronized
    override fun setEnabled(feature: Feature, enabled: Boolean, allowMobileData: Boolean) {
        prefs.edit().putBoolean(enabledKey(feature), enabled).apply()
        if (enabled) {
            // A running download cannot change its network rule: start it again if the choice changed.
            val mobileData = allowMobileData || !specs.getValue(feature).askOnMobileData
            if (prefs.getBoolean(mobileDataKey(feature), false) != mobileData) cancelDownload(feature)
            prefs.edit().putBoolean(mobileDataKey(feature), mobileData).apply()
            start(feature)
        } else {
            jobs.remove(feature)?.cancel()
            cancelDownload(feature)
            scope.launch { unload(feature) }
            set(feature, FeatureStatus.Off(file(feature)?.exists() == true))
        }
    }

    @Synchronized
    override fun deleteDownload(feature: Feature) {
        if (_status.value[feature] !is FeatureStatus.Off) return
        file(feature)?.delete()
        set(feature, FeatureStatus.Off(downloaded = false))
    }

    @Synchronized
    private fun start(feature: Feature) {
        jobs.remove(feature)?.cancel()
        jobs[feature] = scope.launch {
            try {
                val spec = specs.getValue(feature)
                val target = file(feature) ?: throw IOException("This device has no app storage available")
                if (!target.exists()) {
                    val downloaded = download(feature, spec)
                    set(feature, FeatureStatus.Preparing)
                    verify(downloaded, spec)
                    if (!downloaded.renameTo(target)) throw IOException("The download could not be saved")
                }
                set(feature, FeatureStatus.Preparing)
                load(feature, target)
                set(feature, FeatureStatus.Ready)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$feature could not be turned on", e)
                set(feature, FeatureStatus.Failed(e.message ?: "Something went wrong"))
            }
        }
    }

    /** Runs (or re-attaches to) the system download and suspends until it finishes. */
    private suspend fun download(feature: Feature, spec: Spec): File {
        val partial = File(modelDirectory() ?: throw IOException("No app storage available"), spec.fileName + ".part")
        val mobileData = prefs.getBoolean(mobileDataKey(feature), !spec.askOnMobileData)
        var id = prefs.getLong(downloadKey(feature), NO_DOWNLOAD)
        if (id == NO_DOWNLOAD || query(id) == null) {
            partial.delete()
            val request = DownloadManager.Request(Uri.parse(spec.url))
                .setTitle("${context.getString(R.string.app_name)}: ${feature.title}")
                .setDestinationInExternalFilesDir(context, null, "$MODELS/${partial.name}")
                .setAllowedOverMetered(mobileData)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            id = downloads.enqueue(request)
            prefs.edit().putLong(downloadKey(feature), id).apply()
        }
        while (true) {
            val state = query(id) ?: throw IOException("The download was cancelled")
            val progress = if (state.total > 0) state.soFar.toFloat() / state.total else null
            when (state.status) {
                DownloadManager.STATUS_SUCCESSFUL -> break
                DownloadManager.STATUS_FAILED -> {
                    cancelDownload(feature)
                    throw IOException(failureText(state.reason))
                }
                DownloadManager.STATUS_PAUSED -> {
                    // The system reports a Wi-Fi-only download on mobile data either as
                    // "queued for Wi-Fi" or just "waiting for network".
                    val wifi = !mobileData && (state.reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI ||
                        connectivity.isActiveNetworkMetered)
                    set(feature, FeatureStatus.Downloading(progress, if (wifi) "waiting for Wi-Fi" else pauseText(state.reason), wifi))
                }
                DownloadManager.STATUS_PENDING -> {
                    val wifi = !mobileData && connectivity.isActiveNetworkMetered
                    set(feature, FeatureStatus.Downloading(progress, if (wifi) "waiting for Wi-Fi" else null, wifi))
                }
                else -> set(feature, FeatureStatus.Downloading(progress))
            }
            delay(500)
        }
        prefs.edit().remove(downloadKey(feature)).apply()
        return partial
    }

    private suspend fun verify(file: File, spec: Spec) = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (file.length() != spec.bytes || actual != spec.sha256) {
            file.delete()
            throw IOException("The downloaded file was damaged. Please try again.")
        }
    }

    private suspend fun load(feature: Feature, file: File) {
        unload(feature)
        when (feature) {
            Feature.AI -> {
                val loaded = LlamaEngine.load(file) ?: throw IOException("The AI model could not be loaded")
                loaded.warmUp()
                engine = loaded
            }
            Feature.VOICE -> {
                whisper = withContext(Dispatchers.IO) { WhisperModel.load(file) }
                    ?: throw IOException("The speech model could not be loaded")
            }
        }
    }

    private suspend fun unload(feature: Feature) {
        when (feature) {
            Feature.AI -> engine?.also { engine = null }?.close()
            Feature.VOICE -> whisper?.also { whisper = null }?.close()
        }
    }

    private fun cancelDownload(feature: Feature) {
        val id = prefs.getLong(downloadKey(feature), NO_DOWNLOAD)
        if (id != NO_DOWNLOAD) {
            downloads.remove(id) // also deletes the partial file
            prefs.edit().remove(downloadKey(feature)).apply()
        }
    }

    private class DownloadState(val status: Int, val reason: Int, val soFar: Long, val total: Long)

    private fun query(id: Long): DownloadState? =
        downloads.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            DownloadState(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            )
        }

    private fun pauseText(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "waiting for Wi-Fi"
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "waiting for a connection"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "retrying shortly"
        else -> "paused"
    }

    private fun failureText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "There isn't enough free space on this device."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "App storage isn't available."
        DownloadManager.ERROR_CANNOT_RESUME, DownloadManager.ERROR_HTTP_DATA_ERROR,
        DownloadManager.ERROR_TOO_MANY_REDIRECTS, DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
            "The download was interrupted. Check your connection and try again."
        else -> "The download failed (code $reason). Check your connection and try again."
    }

    private fun set(feature: Feature, value: FeatureStatus) = _status.update { it + (feature to value) }

    private fun modelDirectory(): File? = context.getExternalFilesDir(null)?.let { File(it, MODELS).apply { mkdirs() } }
    private fun file(feature: Feature): File? = modelDirectory()?.let { File(it, specs.getValue(feature).fileName) }
    private fun enabledKey(feature: Feature) = "enabled_${feature.name}"
    private fun downloadKey(feature: Feature) = "download_${feature.name}"
    private fun mobileDataKey(feature: Feature) = "mobile_data_${feature.name}"

    companion object {
        private const val TAG = "QuranFeatures"
        private const val MODELS = "models"
        private const val NO_DOWNLOAD = -1L

        @Volatile private var instance: ModelFeatures? = null

        fun get(context: Context): ModelFeatures = instance ?: synchronized(this) {
            instance ?: ModelFeatures(context.applicationContext).also { instance = it }
        }
    }
}
