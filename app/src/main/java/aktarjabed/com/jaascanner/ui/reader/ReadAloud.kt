package aktarjabed.com.jaascanner.ui.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import aktarjabed.com.jaascanner.core.TextReflow
import java.util.Locale

/**
 * Reads paragraphs aloud with the phone's own text-to-speech engine, one piece at a time so it can
 * pause, resume, and tell the screen which paragraph is being spoken. Everything runs on the main
 * thread; the engine's callbacks are posted back to it.
 */
class ReadAloud(
    context: Context,
    private val onParagraph: (Int) -> Unit,
    private val onPlaying: (Boolean) -> Unit,
    private val onProblem: (String) -> Unit,
) {
    private class Piece(val paragraph: Int, val text: String)

    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var engineReady = false
    private var engineFailed = false
    private var pieces: List<Piece> = emptyList()
    private var cursor = 0
    private var playing = false
    private var wantsToPlay = false
    private var utteranceCounter = 0
    private var currentUtterance = ""
    private var rate = 1f
    private var languageTag: String? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            handler.post {
                if (playing && utteranceId == currentUtterance) {
                    cursor++
                    speakCurrent()
                }
            }
        }

        @Deprecated("Required by the abstract class")
        override fun onError(utteranceId: String?) {
            handler.post {
                if (utteranceId == currentUtterance) {
                    setPlaying(false)
                    onProblem("The speech engine stopped. Try again, or pick another voice in the phone's settings.")
                }
            }
        }
    }

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            handler.post {
                if (status == TextToSpeech.SUCCESS) {
                    engineReady = true
                    engine?.setOnUtteranceProgressListener(listener)
                    applyLanguage()
                    engine?.setSpeechRate(rate)
                    if (wantsToPlay) speakCurrent()
                } else {
                    engineFailed = true
                    setPlaying(false)
                    onProblem("This phone has no text-to-speech engine. Install one in Settings, under Text-to-speech.")
                }
            }
        }
    }

    /** Starts reading [paragraphs] from [fromParagraph]. Text with mostly Devanagari letters uses a Hindi voice. */
    fun start(paragraphs: List<String>, fromParagraph: Int) {
        if (engineFailed) {
            onProblem("This phone has no text-to-speech engine. Install one in Settings, under Text-to-speech.")
            return
        }
        pieces = paragraphs.flatMapIndexed { index, text ->
            TextReflow.speechChunks(text).map { Piece(index, it) }
        }
        cursor = pieces.indexOfFirst { it.paragraph >= fromParagraph }.let { if (it < 0) 0 else it }
        languageTag = if (looksDevanagari(paragraphs)) "hi-IN" else null
        if (pieces.isEmpty()) {
            onProblem("There is no text to read.")
            return
        }
        wantsToPlay = true
        if (engineReady) {
            applyLanguage()
            speakCurrent()
        }
    }

    fun pause() {
        wantsToPlay = false
        setPlaying(false)
        engine?.stop()
    }

    fun resume() {
        if (pieces.isEmpty()) return
        wantsToPlay = true
        if (engineReady) speakCurrent()
    }

    fun stop() {
        wantsToPlay = false
        setPlaying(false)
        engine?.stop()
        cursor = 0
    }

    fun setRate(value: Float) {
        rate = value.coerceIn(0.5f, 2f)
        engine?.setSpeechRate(rate)
        // The piece being spoken keeps its old speed; restart it so the change is heard.
        if (playing) {
            engine?.stop()
            speakCurrent()
        }
    }

    fun shutdown() {
        wantsToPlay = false
        playing = false
        engine?.stop()
        engine?.shutdown()
        engine = null
        handler.removeCallbacksAndMessages(null)
    }

    private fun speakCurrent() {
        val tts = engine ?: return
        if (!engineReady) return
        if (cursor >= pieces.size) {
            wantsToPlay = false
            setPlaying(false)
            cursor = 0
            onParagraph(-1)
            return
        }
        val piece = pieces[cursor]
        onParagraph(piece.paragraph)
        currentUtterance = "jas-${utteranceCounter++}"
        setPlaying(true)
        val result = tts.speak(piece.text, TextToSpeech.QUEUE_FLUSH, null, currentUtterance)
        if (result != TextToSpeech.SUCCESS) {
            setPlaying(false)
            onProblem("The speech engine could not start.")
        }
    }

    private fun applyLanguage() {
        val tts = engine ?: return
        val wanted = languageTag?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        val first = tts.setLanguage(wanted)
        if (first == TextToSpeech.LANG_MISSING_DATA || first == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = tts.setLanguage(Locale.ENGLISH)
            if (fallback == TextToSpeech.LANG_MISSING_DATA || fallback == TextToSpeech.LANG_NOT_SUPPORTED) {
                onProblem("No voice is installed for this language. Add one in Settings, under Text-to-speech.")
            } else if (languageTag != null) {
                onProblem("No Hindi voice is installed, so an English voice will read this. Add one in Settings, under Text-to-speech.")
            }
        }
    }

    private fun setPlaying(value: Boolean) {
        if (playing != value) {
            playing = value
            onPlaying(value)
        }
    }

    private fun looksDevanagari(paragraphs: List<String>): Boolean {
        var letters = 0
        var devanagari = 0
        for (paragraph in paragraphs.take(20)) {
            for (ch in paragraph) {
                if (ch.isLetter()) {
                    letters++
                    if (ch.code in 0x0900..0x097F) devanagari++
                }
            }
        }
        return letters > 20 && devanagari * 10 > letters * 3
    }
}
