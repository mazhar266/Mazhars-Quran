package com.hikmah.quran.android

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.hikmah.quran.layout.SharedPrefsLayoutStorage
import com.hikmah.quran.llm.LlamaEngine
import com.hikmah.quran.stt.MicrophoneVoiceInput
import com.hikmah.quran.ui.App
import com.hikmah.quran.voice.CommandInterpreter
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private var permissionAnswer: CompletableDeferred<Boolean>? = null
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionAnswer?.complete(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storage = SharedPrefsLayoutStorage(this)
        // Uses the on-device model if one is installed, otherwise keyword rules only.
        val interpreter = CommandInterpreter(LlamaEngine.shared(this))
        val voice = MicrophoneVoiceInput(this) {
            CompletableDeferred<Boolean>().also {
                permissionAnswer = it
                permissionRequest.launch(Manifest.permission.RECORD_AUDIO)
            }.await()
        }
        MicrophoneVoiceInput.model(this) // start loading the speech model now
        setContent { App(storage, interpreter, voice) }
    }
}
