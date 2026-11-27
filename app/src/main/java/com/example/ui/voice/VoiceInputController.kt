package com.example.ui.voice

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.io.File
import java.util.Locale

private const val TAG = "VoiceInputController"

sealed interface VoiceCaptureState {
    data object Idle : VoiceCaptureState
    data class ListeningSpeech(val partialText: String, val rmsLevel: Float) : VoiceCaptureState
    data object RecordingAudioFallback : VoiceCaptureState
}

class VoiceInputController(
    private val context: Context,
    private val onStateChanged: (VoiceCaptureState) -> Unit,
    private val onTranscriptFinal: (String) -> Unit,
    private val onAudioClipReady: (ByteArray, String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioOutputFile: File? = null
    private var isStopping = false

    fun startListening() {
        isStopping = false
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            startSpeechRecognizer()
        } else {
            startAudioClipFallback()
        }
    }

    fun stopAndFinish() {
        isStopping = true
        speechRecognizer?.let { recognizer ->
            try {
                recognizer.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop SpeechRecognizer cleanly", e)
                releaseSpeechRecognizer()
                onStateChanged(VoiceCaptureState.Idle)
            }
        }
        if (mediaRecorder != null) {
            finishAudioClipFallback(deliver = true)
        }
    }

    fun cancel() {
        isStopping = true
        releaseSpeechRecognizer()
        finishAudioClipFallback(deliver = false)
        onStateChanged(VoiceCaptureState.Idle)
    }

    fun release() {
        cancel()
    }

    private fun startSpeechRecognizer() {
        releaseSpeechRecognizer()
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onStateChanged(VoiceCaptureState.ListeningSpeech("", 0.2f))
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1f)
                    val currentPartial = ""
                    onStateChanged(VoiceCaptureState.ListeningSpeech(currentPartial, normalized))
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.w(TAG, "SpeechRecognizer error code: $error")
                    releaseSpeechRecognizer()
                    // Fall back to direct Gemini audio capture if device speech service is unavailable
                    if (!isStopping && (error == SpeechRecognizer.ERROR_CLIENT ||
                                error == SpeechRecognizer.ERROR_NETWORK ||
                                error == SpeechRecognizer.ERROR_SERVER ||
                                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
                    ) {
                        startAudioClipFallback()
                    } else {
                        onStateChanged(VoiceCaptureState.Idle)
                        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            onError("Couldn't catch that clearly. Please try speaking again or type.")
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim().orEmpty()
                    releaseSpeechRecognizer()
                    onStateChanged(VoiceCaptureState.Idle)
                    if (text.isNotEmpty()) {
                        onTranscriptFinal(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotEmpty()) {
                        onStateChanged(VoiceCaptureState.ListeningSpeech(partial, 0.5f))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            onStateChanged(VoiceCaptureState.ListeningSpeech("", 0.2f))
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "SpeechRecognizer failed to start, switching to audio clip fallback", e)
            releaseSpeechRecognizer()
            startAudioClipFallback()
        }
    }

    private fun releaseSpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    private fun startAudioClipFallback() {
        try {
            val file = File(context.cacheDir, "buddy_voice_${System.currentTimeMillis()}.m4a")
            audioOutputFile = file
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            onStateChanged(VoiceCaptureState.RecordingAudioFallback)
        } catch (e: Exception) {
            Log.e(TAG, "Audio recording fallback failed", e)
            finishAudioClipFallback(deliver = false)
            onStateChanged(VoiceCaptureState.Idle)
            onError("Microphone unavailable right now. You can type your update below.")
        }
    }

    private fun finishAudioClipFallback(deliver: Boolean) {
        val recorder = mediaRecorder
        mediaRecorder = null
        val file = audioOutputFile
        audioOutputFile = null
        if (recorder != null) {
            try {
                recorder.stop()
            } catch (e: Exception) {
                Log.w(TAG, "MediaRecorder stop warning", e)
            } finally {
                try {
                    recorder.release()
                } catch (_: Exception) {
                }
            }
        }
        onStateChanged(VoiceCaptureState.Idle)
        if (deliver && file != null && file.exists()) {
            try {
                val bytes = file.readBytes()
                if (bytes.size > 1000) {
                    onAudioClipReady(bytes, "audio/mp4")
                } else {
                    onError("Voice clip was too short. Try speaking a bit longer.")
                }
            } catch (e: Exception) {
                onError("Failed to read recorded voice note.")
            } finally {
                file.delete()
            }
        } else {
            file?.delete()
        }
    }
}
