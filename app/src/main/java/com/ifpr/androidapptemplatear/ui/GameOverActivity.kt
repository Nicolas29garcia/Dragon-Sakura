package com.ifpr.androidapptemplatear.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.ifpr.androidapptemplatear.MainActivity

class GameOverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val motivo = intent.getStringExtra("motivo") ?: "A partida terminou."
        val painel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        preencherTela(painel, motivo)
        setContentView(painel)
    }

    private fun preencherTela(painel: LinearLayout, motivo: String) {
        painel.addView(TextView(this).apply {
            text = "GAME OVER\n$motivo"
            textSize = 28f
            gravity = Gravity.CENTER
        })
        painel.addView(Button(this).apply {
            text = "Jogar novamente"
            setOnClickListener {
                isEnabled = false
                startActivity(Intent(this@GameOverActivity, MainActivity::class.java))
                finish()
            }
        })
    }
}
