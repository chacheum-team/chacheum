package com.chacheum.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * 한 문장 안내를 소리로 냄. 지금은 Android 기본 TTS를 쓰고,
 * Pleos TextToSpeech SDK(프로젝트 키 필요)로 바꿀 자리임. 엔진이 없으면 화면 글만 보임.
 */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(ctx.applicationContext, this)
    var ready = false
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            App.log.add("음성", "TTS 엔진 없음 · 화면 글로만 안내")
            return
        }
        val r = tts.setLanguage(Locale.KOREAN)
        ready = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        App.log.add("음성", if (ready) "한국어 TTS 준비됨" else "한국어 음성 없음 · 화면 글로만 안내")
    }

    fun say(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chacheum-${System.nanoTime()}")
    }
}
