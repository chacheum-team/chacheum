package com.chacheum.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 한 문장 안내를 소리로 냄. 지금은 Android 기본 TTS를 쓰고,
 * Pleos TextToSpeech SDK(프로젝트 키 필요)로 바꿀 자리임. 엔진이 없으면 화면 글만 보임.
 */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts: TextToSpeech? = runCatching { TextToSpeech(ctx.applicationContext, this) }.getOrNull()
    var ready = false
        private set
    /** 말하는 중이면 화면에 '말하는 중' 알약을 보임. */
    var speaking = false
        private set
    private var pendingText: String? = null
    private var pendingAt = 0L

    override fun onInit(status: Int) {
        val t = tts
        if (status != TextToSpeech.SUCCESS || t == null) {
            App.log.add("음성", "TTS 엔진 없음 · 화면 글로만 안내")
            return
        }
        val r = t.setLanguage(Locale.KOREAN)
        ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        App.log.add("음성", if (ready) "한국어 TTS 준비됨" else "한국어 음성 없음 · 화면 글로만 안내")
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = App.ui { speaking = true; App.changed() }
            override fun onDone(utteranceId: String?) = App.ui { speaking = false; App.changed() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = App.ui { speaking = false; App.changed() }
        })
        // 엔진이 늦게 준비되면, 4초 안에 부탁받은 말만 함(지난 질문을 뒤늦게 읽지 않게)
        pendingText?.let { if (System.currentTimeMillis() - pendingAt < 4_000) say(it) }
        pendingText = null
    }

    fun say(text: String) {
        if (!ready) {
            pendingText = text
            pendingAt = System.currentTimeMillis()
            return
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chacheum-${System.nanoTime()}")
    }
}
