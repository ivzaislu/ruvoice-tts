package ru.kost.ruvoice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import ru.kost.ruvoice.text.Layers

class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // lite без пака: голосов нет — читалка покажет «установить данные» и пришлёт INSTALL_TTS_DATA
        val packs = Packs.installed(filesDir)
        val has = Speaker.hasVoices(packs)
        // русский и язык каждого пака («bel-BLR»): без него язык пака не выбрать в системных настройках синтеза
        val langs = (listOf(Layers.RU) + packs.map { Layers.of(it.lang) }).distinct().map { "${it.locale.isO3Language}-${it.locale.isO3Country}" }
        val result = Intent().apply {
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, ArrayList(if (has) langs else emptyList()))
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, ArrayList(if (has) emptyList() else langs))
        }
        setResult(if (has) TextToSpeech.Engine.CHECK_VOICE_DATA_PASS else TextToSpeech.Engine.CHECK_VOICE_DATA_MISSING_DATA, result)
        finish()
    }
}
