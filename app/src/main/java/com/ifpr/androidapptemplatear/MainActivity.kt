
package com.ifpr.androidapptemplatear

import android.media.MediaPlayer
import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.ifpr.androidapptemplatear.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Música de fundo do Dragon Sakura
        mediaPlayer = MediaPlayer.create(this, R.raw.samurai)

        mediaPlayer?.apply {
            isLooping = true
            start()
        }

        val navView: BottomNavigationView = binding.navView

        val navController =
            findNavController(R.id.nav_host_fragment_activity_main)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_camera,
                R.id.navigation_changeobject
                // R.id.navigation_profile
            )
        )

        navView.setupWithNavController(navController)
    }

    fun pararSomDaPartida() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }

        mediaPlayer = null
    }

    override fun onDestroy() {
        pararSomDaPartida()
        super.onDestroy()
    }
}
