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

    fun registrarEncontro(arquivo: String) {
        if (jogoEncerrado) return
        if (modelosEncontrados.add(arquivo)) {
            _pontuacao.value = (_pontuacao.value ?: 0) + 10
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
        if (!jogoEncerrado) selectedModel.value = modelPath
    }
}
