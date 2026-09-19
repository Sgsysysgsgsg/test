package com.eyad.geysermobile

import android.app.Activity
import android.content.*
import android.graphics.Color
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {
    private val pickKey = 1001
    private lateinit var status: TextView
    private lateinit var statusDot: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var logs: TextView
    private lateinit var start: MaterialButton
    private lateinit var stop: MaterialButton
    private lateinit var authGroup: RadioGroup
    private lateinit var keyStatus: TextView

    private val bg = Color.rgb(10, 12, 16)
    private val card = Color.rgb(20, 23, 29)
    private val card2 = Color.rgb(25, 29, 36)
    private val text = Color.rgb(244, 246, 250)
    private val muted = Color.rgb(157, 166, 180)
    private val accent = Color.rgb(86, 170, 255)
    private val success = Color.rgb(67, 205, 137)
    private val danger = Color.rgb(255, 91, 104)

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

    private fun rounded(color: Int, radius: Int = 18): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun label(value: String, size: Float = 13f): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(muted)
        includeFontPadding = false
    }

    private fun cardView(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(18).toFloat()
        setCardBackgroundColor(card)
        strokeColor = Color.rgb(38, 43, 52)
        strokeWidth = dp(1)
        cardElevation = 0f
        layoutParams = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        }
    }

    private fun input(hint: String, value: String, number: Boolean = false): TextInputLayout {
        val edit = TextInputEditText(this).apply {
            setText(value)
            setTextColor(this@MainActivity.text)
            setHintTextColor(muted)
            textSize = 15f
            setSingleLine(true)
            if (number) inputType = InputType.TYPE_CLASS_NUMBER
        }
        return TextInputLayout(this).apply {
            this.hint = hint
            setHintTextColor(muted)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            boxStrokeColor = Color.rgb(55, 62, 74)
            boxStrokeWidth = dp(1)
            boxStrokeWidthFocused = dp(2)
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
            addView(edit, ViewGroup.LayoutParams(-1, dp(54)))
            tag = edit
            layoutParams = LinearLayout.LayoutParams(-1, dp(68)).apply { bottomMargin = dp(4) }
        }
    }

    private fun titleRow(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 17f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        addView(TextView(this@MainActivity).apply {
            this.text = subtitle
            textSize = 12.5f
            setTextColor(muted)
            setPadding(0, dp(4), 0, 0)
        })
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
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(18))
        }
        val icon = TextView(this).apply {
            text = "G"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(accent, 14)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(12) }
        }
        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "Geyser Mobile"
                textSize = 25f
                setTextColor(this@MainActivity.text)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Bedrock → Java bridge"
                textSize = 12.5f
                setTextColor(muted)
                setPadding(0, dp(3), 0, 0)
            })
        }
        header.addView(icon)
        header.addView(headerText, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header)

        val statusCard = cardView()
        val statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        statusDot = TextView(this).apply {
            text = "●"
            textSize = 16f
            setTextColor(muted)
            layoutParams = LinearLayout.LayoutParams(dp(25), -2)
        }
        status = TextView(this).apply {
            text = "Stopped"
            textSize = 15f
            setTextColor(this@MainActivity.text)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        statusBox.addView(statusDot)
        statusBox.addView(status)
        statusCard.addView(statusBox)
        content.addView(statusCard)

        val serverCard = cardView()
        val serverBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(10))
        }
        serverBox.addView(titleRow("Java Server", "Where Geyser will forward Bedrock players"))
        serverBox.addView(Space(this).apply { minimumHeight = dp(12) })
        val ipLayout = input("Java Server IP / Host", "127.0.0.1")
        val portLayout = input("Java Port", "25565", true)
        serverBox.addView(ipLayout)
        serverBox.addView(portLayout)
        serverCard.addView(serverBox)
        content.addView(serverCard)
        val ip = ipLayout.tag as TextInputEditText
        val port = portLayout.tag as TextInputEditText

        val authCard = cardView()
        val authBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(14))
        }
        authBox.addView(titleRow("Authentication", "Choose how Bedrock players authenticate"))
        authGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val online = radio("Online", "Microsoft authentication", 1)
        val offline = radio("Offline", "No account authentication", 2)
        val floodgate = radio("Floodgate", "Use your Floodgate key", 3)
        authGroup.addView(online); authGroup.addView(offline); authGroup.addView(floodgate)
        online.isChecked = true
        authBox.addView(authGroup)
        val keyButton = MaterialButton(this).apply {
            text = "SELECT FLOODGATE KEY"
            setTextColor(this@MainActivity.text)
            backgroundTintList = android.content.res.ColorStateList.valueOf(card2)
            strokeColor = android.content.res.ColorStateList.valueOf(Color.rgb(55, 62, 74))
            strokeWidth = dp(1)
            cornerRadius = dp(12)
            setPadding(dp(10), 0, dp(10), 0)
        }
        keyStatus = label("No Floodgate key selected")
        keyStatus.setPadding(0, dp(8), 0, 0)
        keyButton.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, pickKey)
        }
        authBox.addView(keyButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) })
        authBox.addView(keyStatus)
        authCard.addView(authBox)
        content.addView(authCard)

        // Bedrock UDP port is intentionally hidden from the main UI. Geyser Mobile uses 19132.
        val bedrockPort = 19132

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        start = MaterialButton(this).apply {
            text = "START GEYSER"
            textSize = 14f
            cornerRadius = dp(14)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        }
        stop = MaterialButton(this).apply {
            text = "STOP"
            textSize = 14f
            cornerRadius = dp(14)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(55, 61, 72))
            isEnabled = false
        }
        actionRow.addView(start, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(7) })
        actionRow.addView(stop, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(7) })
        content.addView(actionRow, LinearLayout.LayoutParams(-1, dp(52)).apply { bottomMargin = dp(12) })

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(39, 44, 52))
            visibility = View.GONE
        }
        progressText = label("Ready")
        progressText.setPadding(2, dp(5), 0, dp(12))
        content.addView(progress, LinearLayout.LayoutParams(-1, dp(5)))
        content.addView(progressText)

        val consoleCard = cardView()
        val consoleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        consoleBox.addView(titleRow("Console", "Live Geyser output"))
        logs = TextView(this).apply {
            text = "Ready.\n"
            textSize = 11.5f
            setTextColor(Color.rgb(210, 216, 226))
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(12), dp(10), dp(12))
            background = rounded(Color.rgb(13, 16, 21), 12)
        }
        val logScroll = ScrollView(this).apply {
            addView(logs, ViewGroup.LayoutParams(-1, -1))
            isFillViewport = true
        }
        consoleBox.addView(logScroll, LinearLayout.LayoutParams(-1, dp(300)).apply { topMargin = dp(10) })
        consoleCard.addView(consoleBox)
        content.addView(consoleCard)

        start.setOnClickListener {
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
            appendLog("Geyser stopped by user.")
        }

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        ContextCompat.registerReceiver(this, receiver, IntentFilter(GeyserService.ACTION_UPDATE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun radio(main: String, sub: String, id: Int): RadioButton = RadioButton(this).apply {
        this.id = id
        text = "$main  •  $sub"
        textSize = 14f
        setTextColor(this@MainActivity.text)
        buttonTintList = android.content.res.ColorStateList.valueOf(accent)
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
        logs.append(value + "\n")
        logs.post {
            (logs.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN)
        }
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

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"
        val kb = value / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return if (mb < 1024) "%.1f MB".format(mb) else "%.2f GB".format(mb / 1024.0)
    }
}
