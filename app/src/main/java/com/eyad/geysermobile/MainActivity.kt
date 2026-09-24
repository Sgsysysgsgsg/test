package com.eyad.geysermobile

import android.app.Activity
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val pickKey = 1001
    private lateinit var status: TextView
    private lateinit var statusDot: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var logs: TextView
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var authGroup: RadioGroup
    private lateinit var keyButton: Button
    private lateinit var keyStatus: TextView

    // Clean dark UI with a subtle purple/pink accent.
    private val bg = Color.rgb(10, 12, 16)
    private val card = Color.rgb(20, 24, 32)
    private val field = Color.rgb(15, 19, 25)
    private val border = Color.rgb(43, 49, 60)
    private val text = Color.rgb(244, 246, 250)
    private val muted = Color.rgb(157, 165, 178)
    private val accent = Color.rgb(160, 92, 245)
    private val accentDark = Color.rgb(116, 68, 181)
    private val success = Color.rgb(70, 218, 155)
    private val danger = Color.rgb(255, 91, 120)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val s = intent?.getStringExtra(GeyserService.EXTRA_STATUS)
            val l = intent?.getStringExtra(GeyserService.EXTRA_LOG)
            if (s != null) setStatus(s)
            if (l != null) appendLog(l)
            val done = intent?.getLongExtra(GeyserService.EXTRA_PROGRESS, -1L) ?: -1L
            val total = intent?.getLongExtra(GeyserService.EXTRA_TOTAL, -1L) ?: -1L
            if (done >= 0) {
                progress.visibility = View.VISIBLE
                if (total > 0) {
                    progress.isIndeterminate = false
                    progress.max = 1000
                    progress.progress = ((done * 1000L) / total).toInt().coerceIn(0, 1000)
                    progressText.text = "Downloading Geyser  •  ${formatBytes(done)} / ${formatBytes(total)}"
                } else {
                    progress.isIndeterminate = true
                    progressText.text = "Downloading Geyser  •  ${formatBytes(done)}"
                }
            }
            when (s) {
                "Running" -> {
                    start.isEnabled = false
                    stop.isEnabled = true
                    progress.visibility = View.GONE
                    progressText.text = "Geyser is running"
                }
                "Error", "Stopped" -> {
                    start.isEnabled = true
                    stop.isEnabled = false
                    if (s == "Error") progress.visibility = View.GONE
                }
            }
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 16, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke > 0) setStroke(dp(1), stroke)
    }

    private fun textView(value: String, size: Float, color: Int = text, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun section(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(textView(title, 17f, text, true))
        addView(textView(subtitle, 12.5f, muted).apply { setPadding(0, dp(5), 0, 0) })
    }

    private fun cardView(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(card, 19, border)
        setPadding(dp(17), dp(17), dp(17), dp(17))
        layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(13) }
    }

    private fun input(title: String, value: String, number: Boolean = false): EditText = EditText(this).apply {
        setText(value)
        setTextColor(this@MainActivity.text)
        setHintTextColor(muted)
        textSize = 15f
        hint = title
        setSingleLine(true)
        inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        background = rounded(field, 13, border)
        setPadding(dp(14), 0, dp(14), 0)
        minHeight = dp(52)
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(10) }
    }

    private fun actionButton(label: String, color: Int, enabled: Boolean = true): Button = Button(this).apply {
        text = label
        textSize = 13.5f
        setTextColor(Color.WHITE)
        isAllCaps = false
        stateListAnimator = null
        background = rounded(color, 14)
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.48f
        minHeight = dp(50)
        minimumHeight = dp(50)
        setPadding(dp(10), 0, dp(10), 0)
        setOnEnabledChangeListenerCompat()
    }

    private fun Button.setOnEnabledChangeListenerCompat() {
        // Keep the disabled state visually quiet without adding animations or extra work.
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> alpha = if (isEnabled) 1f else 0.48f }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(16))
        }
        val icon = TextView(this).apply {
            text = "G"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(accent, 15)
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply { rightMargin = dp(12) }
        }
        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(textView("Geyser Mobile", 25f, text, true))
            addView(textView("Bedrock → Java bridge", 12.5f, muted).apply { setPadding(0, dp(4), 0, 0) })
            addView(textView("Made for RORO ONLY", 10.5f, accent, true).apply { setPadding(0, dp(5), 0, 0) })
        }
        header.addView(icon)
        header.addView(headerText, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header)

        // Friendly intro: no programming knowledge required.
        val intro = TextView(this).apply {
            text = "Simple setup. Enter the Java server and tap Start."
            textSize = 12.5f
            setTextColor(muted)
            setPadding(dp(2), 0, dp(2), dp(15))
        }
        content.addView(intro)

        // Status
        val statusCard = cardView()
        val statusBox = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusDot = textView("●", 15f, muted)
        statusDot.layoutParams = LinearLayout.LayoutParams(dp(25), -2)
        status = textView("Stopped", 15f, text, true)
        statusBox.addView(statusDot)
        statusBox.addView(status)
        statusCard.addView(statusBox)
        content.addView(statusCard)

        // Java server
        val serverCard = cardView()
        serverCard.addView(section("Java Server", "Where Bedrock players will connect through Geyser"))
        serverCard.addView(Space(this).apply { minimumHeight = dp(12) })
        val ip = input("Java Server IP / Host", "127.0.0.1")
        val port = input("Java Port", "25565", true)
        serverCard.addView(ip)
        serverCard.addView(port)
        content.addView(serverCard)

        // Authentication
        val authCard = cardView()
        authCard.addView(section("Login", "Pick the login method used by your Java server"))
        authGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, dp(8), 0, dp(2))
        }
        val online = radio("Online", "Microsoft account", 1)
        val offline = radio("Offline", "No account authentication", 2)
        val floodgate = radio("Floodgate", "Floodgate key", 3)
        authGroup.addView(online)
        authGroup.addView(offline)
        authGroup.addView(floodgate)
        online.isChecked = true
        authCard.addView(authGroup)

        keyButton = actionButton("Select Floodgate key (.pem)", Color.rgb(47, 38, 61))
        keyStatus = textView("Floodgate key not needed", 12f, muted)
        keyStatus.setPadding(dp(2), dp(7), 0, 0)
        keyButton.visibility = View.GONE
        keyStatus.visibility = View.GONE
        keyButton.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, pickKey)
        }
        authCard.addView(keyButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) })
        authCard.addView(keyStatus)
        authGroup.setOnCheckedChangeListener { _, checkedId ->
            val flood = checkedId == 3
            keyButton.visibility = if (flood) View.VISIBLE else View.GONE
            keyStatus.visibility = if (flood) View.VISIBLE else View.GONE
            if (flood && java.io.File(filesDir, "floodgate-key.pem").exists()) {
                keyStatus.text = "✓ Floodgate key ready"
            }
        }
        content.addView(authCard)

        // Bedrock port is intentionally hidden. It is fixed to 19132.
        val bedrockPort = 19132

        // Main actions
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        start = actionButton("Start Geyser", accent)
        stop = actionButton("Stop", Color.rgb(49, 54, 63), false)
        actionRow.addView(start, LinearLayout.LayoutParams(0, dp(53), 1f).apply { rightMargin = dp(5) })
        actionRow.addView(stop, LinearLayout.LayoutParams(0, dp(53), 1f).apply { leftMargin = dp(5) })
        content.addView(actionRow, LinearLayout.LayoutParams(-1, dp(53)).apply { bottomMargin = dp(12) })

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(43, 49, 60))
            visibility = View.GONE
        }
        progressText = textView("Ready", 12f, muted)
        progressText.setPadding(2, dp(5), 0, dp(11))
        content.addView(progress, LinearLayout.LayoutParams(-1, dp(5)))
        content.addView(progressText)

        // Console kept compact and bounded to avoid unnecessary memory/battery use.
        val consoleCard = cardView()
        consoleCard.addView(section("Console", "Live Geyser status"))
        logs = TextView(this).apply {
            text = "Ready.\n"
            textSize = 11f
            setTextColor(Color.rgb(205, 211, 221))
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(11), dp(10), dp(11))
            background = rounded(Color.rgb(13, 16, 21), 12, border)
        }
        val logScroll = ScrollView(this).apply { addView(logs, ViewGroup.LayoutParams(-1, -2)); isFillViewport = true }
        consoleCard.addView(logScroll, LinearLayout.LayoutParams(-1, dp(250)).apply { topMargin = dp(10) })
        content.addView(consoleCard)

        start.setOnClickListener {
            if (getSharedPreferences("geyser_state", MODE_PRIVATE).getBoolean("running", false)) {
                setStatus("Running")
                appendLog("Geyser is already running. No second instance was started.")
                start.isEnabled = false
                stop.isEnabled = true
                return@setOnClickListener
            }
            val mode = when (authGroup.checkedRadioButtonId) { 2 -> "offline"; 3 -> "floodgate"; else -> "online" }
            if (mode == "floodgate" && !java.io.File(filesDir, "floodgate-key.pem").exists()) {
                setStatus("Error")
                appendLog("ERROR: Select a Floodgate .pem key first.")
                return@setOnClickListener
            }
            logs.text = "Geyser Mobile Console\n────────────────────────\n"
            progress.visibility = View.VISIBLE
            progress.isIndeterminate = true
            progressText.text = "Preparing Geyser…"
            setStatus("Starting…")
            start.isEnabled = false
            stop.isEnabled = true
            start.alpha = 0.48f
            stop.alpha = 1f
            val serviceIntent = Intent(this, GeyserService::class.java).apply {
                putExtra("javaHost", ip.text?.toString()?.trim() ?: "127.0.0.1")
                putExtra("javaPort", port.text?.toString()?.toIntOrNull() ?: 25565)
                putExtra("bedrockPort", bedrockPort)
                putExtra("authType", mode)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        }
        stop.setOnClickListener {
            stopService(Intent(this, GeyserService::class.java))
            setStatus("Stopped")
            progress.visibility = View.GONE
            progressText.text = "Ready"
            start.isEnabled = true
            stop.isEnabled = false
            start.alpha = 1f
            stop.alpha = 0.48f
            appendLog("Geyser stopped by user.")
        }

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ContextCompat.registerReceiver(this, receiver, IntentFilter(GeyserService.ACTION_UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED)

        // Restore the server state when the UI is reopened while the foreground
        // service is still hosting Geyser in its dedicated process.
        if (getSharedPreferences("geyser_state", MODE_PRIVATE).getBoolean("running", false)) {
            setStatus("Running")
            start.isEnabled = false
            stop.isEnabled = true
            start.alpha = 0.48f
            stop.alpha = 1f
            progress.visibility = View.GONE
            progressText.text = "Geyser is running in the background"
        }
    }

    private fun radio(main: String, sub: String, id: Int): RadioButton = RadioButton(this).apply {
        this.id = id
        text = "$main  •  $sub"
        textSize = 14f
        setTextColor(this@MainActivity.text)
        buttonTintList = ColorStateList.valueOf(accent)
        minHeight = dp(46)
        setPadding(0, 0, 0, 0)
    }

    private fun setStatus(value: String) {
        status.text = value
        val running = value.equals("Running", true)
        val error = value.equals("Error", true)
        statusDot.setTextColor(if (running) success else if (error) danger else muted)
        statusDot.text = "●"
    }

    private fun appendLog(value: String) {
        // Keep only the most recent 180 lines so long-running servers do not grow the UI endlessly.
        val current = logs.text.toString().split('\n').takeLast(180).joinToString("\n")
        logs.text = (current + if (current.endsWith("\n")) value else "\n$value").takeLast(14000)
        logs.post { (logs.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN) }
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
                keyStatus.text = "✓ $name imported • Floodgate ready"
            } catch (e: Exception) {
                keyStatus.text = "Failed to import key: ${e.message}"
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

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"
        val kb = value / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return if (mb < 1024) "%.1f MB".format(mb) else "%.2f GB".format(mb / 1024.0)
    }
}
