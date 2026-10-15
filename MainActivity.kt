package com.example.babymonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var isMonitoring = false
    private var audioRecord: AudioRecord? = null
    private var monitoringThread: Thread? = null

    private lateinit var etPhoneNumber: EditText
    private lateinit var sbSensitivity: SeekBar
    private lateinit var tvThresholdLabel: TextView
    private lateinit var tvSoundLevel: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnToggle: Button

    private var soundThreshold = 4000
    private var cryDurationCounter = 0
    private var isCooldown = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        sbSensitivity = findViewById(R.id.sbSensitivity)
        tvThresholdLabel = findViewById(R.id.tvThresholdLabel)
        tvSoundLevel = findViewById(R.id.tvSoundLevel)
        tvStatus = findViewById(R.id.tvStatus)
        btnToggle = findViewById(R.id.btnToggle)

        checkAndRequestPermissions()

        sbSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                soundThreshold = 8000 - (progress * 70)
                if (soundThreshold < 500) soundThreshold = 500
                tvThresholdLabel.text = "حد الصوت المطلوب للاتصال: $soundThreshold"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnToggle.setOnClickListener {
            if (isMonitoring) {
                stopMonitoring()
            } else {
                val phone = etPhoneNumber.text.toString().trim()
                if (phone.isEmpty()) {
                    Toast.makeText(this, "يرجى إدخال رقم الهاتف أولاً!", Toast.LENGTH_SHORT).show()
                } else if (checkAndRequestPermissions()) {
                    startMonitoring(phone)
                }
            }
        }
    }

    private fun startMonitoring(phoneNumber: String) {
        val sampleRate = 8000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()
        isMonitoring = true
        cryDurationCounter = 0

        btnToggle.text = "إيقاف المراقبة"
        btnToggle.setBackgroundColor(0xFFC0392B.toInt())
        tvStatus.text = "الحالة: جاري الاستماع للطفل..."

        monitoringThread = Thread {
            val buffer = ShortArray(bufferSize)
            while (isMonitoring) {
                val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                var maxAmplitude = 0
                for (i in 0 until readSize) {
                    if (abs(buffer[i].toInt()) > maxAmplitude) {
                        maxAmplitude = abs(buffer[i].toInt())
                    }
                }

                mainHandler.post {
                    tvSoundLevel.text = "مستوى الصوت الحالي: $maxAmplitude"
                }

                if (maxAmplitude > soundThreshold && !isCooldown) {
                    cryDurationCounter++
                    mainHandler.post {
                        tvStatus.text = "الحالة: تم رصد صوت مرتفع ($cryDurationCounter)..."
                    }

                    if (cryDurationCounter >= 6) {
                        cryDurationCounter = 0
                        isCooldown = true
                        mainHandler.post {
                            tvStatus.text = "الحالة: بكاء مؤكد! جاري الاتصال..."
                            makePhoneCall(phoneNumber)
                        }

                        Thread.sleep(60000)
                        isCooldown = false
                    }
                } else {
                    if (cryDurationCounter > 0) cryDurationCounter--
                    if (!isCooldown) {
                        mainHandler.post {
                            tvStatus.text = "الحالة: جاري الاستماع للطفل..."
                        }
                    }
                }

                Thread.sleep(500)
            }
        }
        monitoringThread?.start()
    }

    private fun stopMonitoring() {
        isMonitoring = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        btnToggle.text = "بدء المراقبة"
        btnToggle.setBackgroundColor(0xFF27AE60.toInt())
        tvStatus.text = "الحالة: متوقف"
        tvSoundLevel.text = "مستوى الصوت الحالي: 0"
    }

    private fun makePhoneCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val callIntent = Intent(Intent.ACTION_CALL)
            callIntent.data = Uri.parse("tel:$phoneNumber")
            startActivity(callIntent)
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CALL_PHONE)
        }

        return if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 101)
            false
        } else {
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isMonitoring) {
            stopMonitoring()
        }
    }
}
