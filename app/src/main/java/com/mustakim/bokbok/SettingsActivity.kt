package com.mustakim.bokbok

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREF_PTT = "pref_ptt"
        const val PREF_NOISE_SUPP = "pref_noise_supp"
        const val PREF_DUCK = "pref_duck"
        const val PREF_RECEIVE_VOL = "pref_receive_vol" // integer percentage (10..200)

        fun open(ctx: Context) {
            val i = Intent(ctx, SettingsActivity::class.java)
            ctx.startActivity(i)
        }
    }

    private val prefs by lazy { getSharedPreferences("bokbok_prefs", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val swPtt = findViewById<Switch>(R.id.switchPushToTalk)
        val swNoise = findViewById<Switch>(R.id.switchNoiseSupp)
        val swDuck = findViewById<Switch>(R.id.switchDuck)
        val sbReceive = findViewById<SeekBar>(R.id.seekReceiveVolume)
        val tvReceiveVal = findViewById<TextView>(R.id.receiveVolValue)
        val btnDone = findViewById<Button>(R.id.btnDone)

        // load values
        val ptt = prefs.getBoolean(PREF_PTT, false)
        val noise = prefs.getBoolean(PREF_NOISE_SUPP, true)
        val duck = prefs.getBoolean(PREF_DUCK, true)
        var rVol = prefs.getInt(PREF_RECEIVE_VOL, 100).coerceIn(10, 200)

        // clamp rVol
        if (rVol < 10) rVol = 10
        if (rVol > 200) rVol = 200

        swPtt.isChecked = ptt
        swNoise.isChecked = noise
        swDuck.isChecked = duck
        sbReceive.progress = rVol
        tvReceiveVal.text = "${rVol}%"

        swPtt.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_PTT, isChecked).apply()
        }
        swNoise.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_NOISE_SUPP, isChecked).apply()
        }
        swDuck.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_DUCK, isChecked).apply()
        }

        sbReceive.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var v = progress
                if (v < 10) v = 10
                tvReceiveVal.text = "$v%"
                prefs.edit().putInt(PREF_RECEIVE_VOL, v).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnDone.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
    }
}
