package com.ifpr.androidapptemplatear.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedModelViewModel : ViewModel() {
    val selectedModel = MutableLiveData<String>()

    private val _vidas = MutableLiveData(3)
    val vidas: LiveData<Int> = _vidas

    var jogoEncerrado = false
        private set

    private var ultimoDano: Long? = null

    private val _pontuacao = MutableLiveData(0)
    val pontuacao: LiveData<Int> = _pontuacao
    private val modelosEncontrados = mutableSetOf<String>()

    private val _dragonHp = MutableLiveData(0)
    val dragonHp: LiveData<Int> = _dragonHp

    private val _portal = MutableLiveData<String?>(null)
    val portal: LiveData<String?> = _portal

    private val capturadosNaPartida = mutableSetOf<String>()

    fun iniciarEncontro(arquivo: String, portal: String) {
        if (jogoEncerrado) return
        _portal.value = portal
        _dragonHp.value = SakuraRules.profile(arquivo)?.maxHp ?: 0
        selectedModel.value = arquivo
        registrarEncontro(arquivo)
    }

    fun atacarDragao(arquivo: String): Boolean {
        if (jogoEncerrado || selectedModel.value != arquivo ||
            SakuraRules.profile(arquivo) == null || foiCapturado(arquivo)) return false
        val hp = _dragonHp.value ?: 0
        if (hp <= 0) return false
        _dragonHp.value = SakuraRules.remainingHp(hp)
        return true
    }

    fun foiCapturado(arquivo: String): Boolean = arquivo in capturadosNaPartida

    fun registrarCaptura(arquivo: String): Boolean {
        if (jogoEncerrado || selectedModel.value != arquivo ||
            SakuraRules.profile(arquivo) == null || _dragonHp.value != 0) return false
        return capturadosNaPartida.add(arquivo)
    }

    fun registrarEncontro(arquivo: String) {
        if (jogoEncerrado) return
        if (modelosEncontrados.add(arquivo)) {
            _pontuacao.value = (_pontuacao.value ?: 0) + (SakuraRules.profile(arquivo)?.points ?: 10)
        }
    }

    fun reiniciarPontuacao() {
        modelosEncontrados.clear()
        _pontuacao.value = 0
    }

    fun perderVida(agora: Long): Boolean {
        if (jogoEncerrado || (_vidas.value ?: 0) == 0) return false
        val anterior = ultimoDano
        if (anterior != null && agora - anterior < 1000L) return false

        ultimoDano = agora
        _vidas.value = ((_vidas.value ?: 3) - 1).coerceAtLeast(0)
        return true
    }

    fun encerrarJogo(): Boolean {
        if (jogoEncerrado) return false
        jogoEncerrado = true
        return true
    }


    fun selectModel(modelPath: String) {
        if (jogoEncerrado) return
        if (selectedModel.value != modelPath || _portal.value != null) {
            _portal.value = null
            _dragonHp.value = SakuraRules.profile(modelPath)?.maxHp ?: 0
            selectedModel.value = modelPath
        }
    }
}
