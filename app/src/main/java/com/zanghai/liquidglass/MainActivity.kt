package com.zanghai.liquidglass

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.zanghai.liquidglass.databinding.ActivityMainBinding
import com.zanghai.liquidglass.lib.LiquidGlassConfig
import com.zanghai.liquidglass.lib.LiquidGlassView

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding: ActivityMainBinding
        get() = checkNotNull(_binding) { "Activity has been destroyed" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupPresets()
    }
    
    private fun setupPresets() {
        val presets = listOf(
            "Clear" to LiquidGlassConfig.CLEAR_GLASS,
            "Water" to LiquidGlassConfig.WATER,
            "Crystal" to LiquidGlassConfig.CRYSTAL,
            "Frosted" to LiquidGlassConfig.FROSTED,
            "iOS Style" to LiquidGlassConfig.IOS_STYLE
        )
        
        presets.forEach { (name, config) ->
            val chip = TextView(this).apply {
                text = name
                setTextColor(Color.WHITE)
                setBackgroundColor(0x33FFFFFF)
                setPadding(32, 16, 32, 16)
                gravity = Gravity.CENTER
                
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = 16
                layoutParams = params
                
                setOnClickListener {
                    binding.glassTop.setConfig(config)
                    binding.glassBottom.setConfig(config)
                    
                    // Reset all chip backgrounds
                    for (i in 0 until binding.presetChips.childCount) {
                        binding.presetChips.getChildAt(i).setBackgroundColor(0x33FFFFFF)
                    }
                    // Highlight selected
                    setBackgroundColor(0x88FFFFFF.toInt())
                }
            }
            binding.presetChips.addView(chip)
        }
        
        // Select first preset by default
        binding.presetChips.getChildAt(0)?.performClick()
    }

    override fun onPause() {
        super.onPause()
        binding.glassTop.pause()
        binding.glassBottom.pause()
    }

    override fun onResume() {
        super.onResume()
        binding.glassTop.resume()
        binding.glassBottom.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
