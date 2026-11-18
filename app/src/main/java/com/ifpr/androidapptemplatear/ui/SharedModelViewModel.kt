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
