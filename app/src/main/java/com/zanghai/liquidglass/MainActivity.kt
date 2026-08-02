package com.zanghai.liquidglass

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.zanghai.liquidglass.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding
        get() = checkNotNull(_binding) { "Activity has been destroyed" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onPause() {
        super.onPause()
        binding.glassOrb.pause()
    }

    override fun onResume() {
        super.onResume()
        binding.glassOrb.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
