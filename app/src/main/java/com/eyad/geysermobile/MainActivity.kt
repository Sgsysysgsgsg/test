package com.eyad.geysermobile

import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val pickKey = 1001
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var logs: TextView
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var authGroup: RadioGroup
    private lateinit var keyStatus: TextView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val s = intent?.getStringExtra(GeyserService.EXTRA_STATUS)
            val l = intent?.getStringExtra(GeyserService.EXTRA_LOG)
            if (s != null) status.text = "● $s"
            if (l != null) {
                logs.append(l + "\n")
                logs.post { logs.parent?.let { (it as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN) } }
            }
            val done = intent?.getLongExtra(GeyserService.EXTRA_PROGRESS, -1L) ?: -1L
            val total = intent?.getLongExtra(GeyserService.EXTRA_TOTAL, -1L) ?: -1L
            if (done >= 0) {
                progress.visibility = ProgressBar.VISIBLE
                if (total > 0) {
                    progress.isIndeterminate = false
                    progress.max = 1000
                    progress.progress = ((done * 1000L) / total).toInt().coerceIn(0, 1000)
                    progressText.text = "Downloading Geyser: ${formatBytes(done)} / ${formatBytes(total)}"
                } else {
                    progress.isIndeterminate = true
                    progressText.text = "Downloading Geyser… ${formatBytes(done)}"
                }
            }
            if (s == "Running") {
                start.isEnabled = false
                stop.isEnabled = true
                progress.visibility = ProgressBar.GONE
                progressText.text = "Geyser is running"
            } else if (s == "Error" || s == "Stopped") {
                start.isEnabled = true
                stop.isEnabled = false
                if (s == "Error") progress.visibility = ProgressBar.GONE
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 24)
        }
        fun field(hint: String, value: String, number: Boolean = false) = EditText(this).apply {
            this.hint = hint
            setText(value)
            if (number) inputType = 2
            layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(this).apply { text = "Geyser Mobile"; textSize = 30f }
        status = TextView(this).apply { text = "● Stopped"; textSize = 18f; setPadding(0, 12, 0, 8) }
        val ip = field("Java Server IP", "127.0.0.1")
        val port = field("Java Port", "25565", true)
        val bedrock = field("Bedrock Port", "19132", true)
        val authTitle = TextView(this).apply { text = "Authentication Mode"; textSize = 18f; setPadding(0, 18, 0, 6) }
        authGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val online = RadioButton(this).apply { text = "Online (Microsoft authentication)"; id = 1 }
        val offline = RadioButton(this).apply { text = "Offline"; id = 2 }
        val floodgate = RadioButton(this).apply { text = "Floodgate"; id = 3 }
        authGroup.addView(online); authGroup.addView(offline); authGroup.addView(floodgate); online.isChecked = true

        val keyButton = Button(this).apply { text = "SELECT FLOODGATE KEY (.PEM) — OPTIONAL" }
        keyStatus = TextView(this).apply { text = "No Floodgate key selected"; setPadding(0, 0, 0, 10) }
        keyButton.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, pickKey)
        }

        start = Button(this).apply { text = "START GEYSER" }
        stop = Button(this).apply { text = "STOP GEYSER"; isEnabled = false }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; visibility = ProgressBar.GONE
        }
        progressText = TextView(this).apply { text = "Ready."; setPadding(0, 8, 0, 8) }
        logs = TextView(this).apply { text = "Console\nReady.\n"; setTextIsSelectable(true) }
        val scroll = ScrollView(this).apply { addView(logs) }

        start.setOnClickListener {
            val mode = when (authGroup.checkedRadioButtonId) { 2 -> "offline"; 3 -> "floodgate"; else -> "online" }
            if (mode == "floodgate" && !java.io.File(filesDir, "floodgate-key.pem").exists()) {
                status.text = "● Error"
                logs.append("ERROR: Select a Floodgate .pem key first.\n")
                return@setOnClickListener
            }
            logs.text = "Console\n"
            progress.visibility = ProgressBar.VISIBLE
            progress.isIndeterminate = true
            progressText.text = "Preparing Geyser…"
            status.text = "● Starting…"
            start.isEnabled = false
            stop.isEnabled = true
            val intent = Intent(this, GeyserService::class.java).apply {
                putExtra("javaHost", ip.text.toString().trim())
                putExtra("javaPort", port.text.toString().toIntOrNull() ?: 25565)
                putExtra("bedrockPort", bedrock.text.toString().toIntOrNull() ?: 19132)
                putExtra("authType", mode)
            }
            androidx.core.content.ContextCompat.startForegroundService(this, intent)
        }
        stop.setOnClickListener {
            stopService(Intent(this, GeyserService::class.java))
            status.text = "● Stopped"
            progress.visibility = ProgressBar.GONE
            progressText.text = "Ready."
            start.isEnabled = true
            stop.isEnabled = false
            logs.append("Geyser stopped by user.\n")
        }

        box.addView(title); box.addView(status); box.addView(ip); box.addView(port); box.addView(bedrock)
        box.addView(authTitle); box.addView(authGroup); box.addView(keyButton); box.addView(keyStatus)
        box.addView(start); box.addView(stop); box.addView(progress); box.addView(progressText); box.addView(scroll,
            LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(box)

        androidx.core.content.ContextCompat.registerReceiver(
            this, receiver, IntentFilter(GeyserService.ACTION_UPDATE), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickKey && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val name = queryDisplayName(uri) ?: "floodgate-key.pem"
                if (!name.lowercase().endsWith(".pem")) {
                    keyStatus.text = "Please select a .pem Floodgate key"
                    return
                }
                contentResolver.openInputStream(uri)?.use { input ->
                    openFileOutput("floodgate-key.pem", MODE_PRIVATE).use { output -> input.copyTo(output) }
                }
                keyStatus.text = "✓ $name imported (Floodgate key ready)"
            } catch (e: Exception) {
                keyStatus.text = "Failed to import key: ${e.message}"
            }
        }
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"
        val kb = value / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return if (mb < 1024) "%.1f MB".format(mb) else "%.2f GB".format(mb / 1024.0)
    }
}
