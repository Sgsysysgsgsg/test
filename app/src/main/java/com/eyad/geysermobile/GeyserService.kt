package com.eyad.geysermobile

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.system.Os
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import java.net.DatagramSocket
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import kotlin.math.max

class GeyserService : Service() {
    private external fun nativeLaunchJava(
        javaHome: String,
        jarPath: String,
        logFile: String,
        args: Array<String>
    ): Int

    init {
        System.loadLibrary("geyserlauncher")
    }

    companion object {
        private const val PREFS = "geyser_state"
        private const val PREF_RUNNING = "running"
        private const val PREF_HOST = "host"
        private const val PREF_PORT = "port"
        private const val PREF_BEDROCK = "bedrock"
        private const val PREF_AUTH = "auth"
        const val ACTION_UPDATE = "com.eyad.geysermobile.UPDATE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_LOG = "log"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_ERROR = "error"
    }

    private val downloadUrl =
        "https://download.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/standalone"
    private var worker: Thread? = null
    @Volatile private var nativeExitCode: Int = -999
    @Volatile private var stopRequested = false

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("geyser", "Geyser Mobile", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, "geyser")
            .setContentTitle("Geyser Mobile")
            .setContentText("Preparing Geyser…")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                7,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(7, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        stopRequested = false
        if (worker?.isAlive == true) {
            emit(status = "Already running", log = "A Geyser startup is already in progress.")
            return START_STICKY
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val auth = intent?.getStringExtra("authType") ?: prefs.getString(PREF_AUTH, "online") ?: "online"
        val host = intent?.getStringExtra("javaHost") ?: prefs.getString(PREF_HOST, "127.0.0.1") ?: "127.0.0.1"
        val javaPort = intent?.getIntExtra("javaPort", prefs.getInt(PREF_PORT, 25565)) ?: prefs.getInt(PREF_PORT, 25565)
        val bedrockPort = intent?.getIntExtra("bedrockPort", prefs.getInt(PREF_BEDROCK, 19132)) ?: prefs.getInt(PREF_BEDROCK, 19132)

        prefs.edit()
            .putString(PREF_AUTH, auth)
            .putString(PREF_HOST, host)
            .putInt(PREF_PORT, javaPort)
            .putInt(PREF_BEDROCK, bedrockPort)
            .apply()

        worker = Thread {
            try {
                var restartCount = 0
                while (!stopRequested) {
                emit(status = "Preparing", log = "Starting Geyser Mobile…")
                val dir = File(filesDir, "geyser").apply { mkdirs() }
                val jar = File(dir, "Geyser-Standalone.jar")

                if (!jar.exists() || jar.length() < 100_000) {
                    emit(status = "Downloading Geyser", log = "Geyser Standalone is not downloaded yet.")
                    download(downloadUrl, jar)
                    emit(status = "Download complete", log = "Geyser Standalone downloaded: ${formatBytes(jar.length())}")
                } else {
                    emit(status = "Geyser ready", log = "Using cached Geyser: ${formatBytes(jar.length())}")
                }

                val key = File(filesDir, "floodgate-key.pem")
                if (auth == "floodgate" && !key.exists()) {
                    throw IOException("Floodgate mode selected, but no .pem key is installed.")
                }

                if (!canBindUdpPort(bedrockPort)) {
                    throw IOException("Bedrock UDP port $bedrockPort is already in use. Stop the previous Geyser instance and try again.")
                }

                val config = File(dir, "config.yml")
                val keyPath = if (auth == "floodgate") key.absolutePath.replace("\\", "/") else "key.pem"
                config.writeText(
                    """
                    bedrock:
                      address: 0.0.0.0
                      port: $bedrockPort
                    remote:
                      address: $host
                      port: $javaPort
                      auth-type: $auth
                    floodgate-key-file: $keyPath
                    """.trimIndent() + "\n"
                )
                val runtime = ensureBundledRuntime()
                emit(status = "Starting Java 21", log = "Launching Android-native Java 21 runtime…")

                val consoleFile = File(dir, "console.log")
                consoleFile.delete()

                val launcherThread = Thread {
                    nativeExitCode = nativeLaunchJava(
                        runtime.absolutePath,
                        jar.absolutePath,
                        consoleFile.absolutePath,
                        arrayOf("-jar", jar.absolutePath, "--nogui")
                    )
                }.also { it.start() }

                var announcedJava = false
                var announcedRunning = false
                var readOffset = 0L
                while (launcherThread.isAlive) {
                    val length = consoleFile.length()
                    if (length > readOffset) {
                        RandomAccessFile(consoleFile, "r").use { raf ->
                            raf.seek(readOffset)
                            while (true) {
                                val line = raf.readLine() ?: break
                                val clean = String(line.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                                if (clean.isNotBlank()) {
                                    emit(log = clean, running = true)
                                    if (!announcedJava) {
                                        announcedJava = true
                                        emit(status = "Java 21 ready", log = "Native Java 21 launcher is running.")
                                    }
                                    if (!announcedRunning && (clean.contains("Geyser", true) || clean.contains("started", true))) {
                                        announcedRunning = true
                                        setServiceRunning(true)
                                        emit(status = "Running", log = "Geyser process is starting…", running = true)
                                    }
                                }
                            }
                            readOffset = raf.filePointer
                        }
                    }
                    Thread.sleep(100)
                }

                launcherThread.join()

                // Read any final output written immediately before exit.
                if (consoleFile.exists() && consoleFile.length() > readOffset) {
                    RandomAccessFile(consoleFile, "r").use { raf ->
                        raf.seek(readOffset)
                        while (true) {
                            val line = raf.readLine() ?: break
                            val clean = String(line.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                            if (clean.isNotBlank()) emit(log = clean, running = false)
                        }
                    }
                }

                val exit = nativeExitCode
                if (exit == 0) {
                    setServiceRunning(false)
                    emit(status = "Stopped", log = "Geyser stopped.", running = false)
                    break
                }

                val lastLog = consoleFile.takeIf { it.exists() }?.readText()?.takeLast(6000) ?: ""
                val bindFailure = lastLog.contains("Address already in use", ignoreCase = true)
                if (bindFailure) {
                    setServiceRunning(false)
                    emit(status = "Error", log = "ERROR: UDP port $bedrockPort is already in use. Geyser was not restarted.", error = "UDP port in use", running = false)
                    break
                }

                if (stopRequested || restartCount >= 3) {
                    setServiceRunning(false)
                    emit(status = "Error", log = "ERROR: Java/Geyser exited with code $exit after ${restartCount} automatic restart(s).", error = "Geyser exited with code $exit", running = false)
                    break
                }

                restartCount++
                val delayMs = 1500L * restartCount
                emit(status = "Recovering", log = "Geyser stopped unexpectedly (code $exit). Restarting in ${delayMs / 1000.0}s…", running = false)
                SystemClock.sleep(delayMs)
            }
            } catch (t: Throwable) {
                android.util.Log.e("GeyserMobile", "Geyser failed", t)
                if (!stopRequested) {
                    setServiceRunning(false)
                    emit(status = "Error", log = "ERROR: ${t.message ?: t.javaClass.simpleName}", error = t.message ?: "Unknown error", running = false)
                }
            } finally {
                if (!stopRequested) stopSelf()
            }
        }.also { it.start() }

        return START_STICKY
    }

    private fun setServiceRunning(running: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_RUNNING, running).apply()
    }

    private fun isServiceRunning(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(PREF_RUNNING, false)

    private fun ensureBundledRuntime(): File {
        val runtime = File(filesDir, "runtime")
        val java = File(runtime, "bin/java")
        if (java.exists()) return runtime

        emit(status = "Preparing Java 21", log = "Looking for bundled Java 21 archive in APK…")
        runtime.mkdirs()

        val archiveName = assets.list("")?.firstOrNull {
            it.startsWith("jre21-aarch64") && (
                it.endsWith(".tar.xz") || it.endsWith(".tar.gz") ||
                it.endsWith(".tar") || it.endsWith(".zip")
            )
        } ?: throw IOException("Java 21 archive was not bundled into the APK.")

        emit(status = "Extracting Java 21", log = "Bundled runtime found: $archiveName")
        assets.open(archiveName).use { input ->
            when {
                archiveName.endsWith(".tar.xz") -> {
                    XZCompressorInputStream(input).use { xz ->
                        extractTar(xz, runtime)
                    }
                }
                archiveName.endsWith(".tar.gz") -> {
                    GZIPInputStream(input).use { gz ->
                        extractTar(gz, runtime)
                    }
                }
                archiveName.endsWith(".tar") -> extractTar(input, runtime)
                archiveName.endsWith(".zip") -> extractZip(input, runtime)
            }
        }

        val javaAfter = findRuntimeJava(runtime)
            ?: throw IOException("Java 21 archive was extracted, but bin/java was not found.")

        val root = javaAfter.parentFile?.parentFile
        if (root != null && root.absolutePath != runtime.absolutePath) {
            root.listFiles()?.forEach { child ->
                child.copyRecursively(File(runtime, child.name), overwrite = true)
            }
        }

        val finalJava = File(runtime, "bin/java")
        if (!finalJava.exists()) throw IOException("Java 21 extraction completed but bin/java is missing.")
        makeRuntimeExecutable(runtime)
        if (!finalJava.canExecute()) {
            throw IOException("Java 21 was extracted but Android could not mark bin/java executable.")
        }
        emit(status = "Java 21 ready", log = "Java 21 runtime extracted successfully.\nExecutable: true")
        return runtime
    }

    private fun makeRuntimeExecutable(runtime: File) {
        // Java/Kotlin File#setExecutable() can silently fail on some Android filesystems.
        // Apply the POSIX mode explicitly and verify the result.
        runtime.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            try {
                if (file.parentFile?.name == "bin" || file.path.contains(File.separator + "bin" + File.separator)) {
                    Os.chmod(file.absolutePath, 0x1ED) // 0755
                }
            } catch (_: Throwable) {
                file.setExecutable(true, false)
            }
        }

        val java = File(runtime, "bin/java")
        try {
            Os.chmod(java.absolutePath, 0x1ED) // 0755
        } catch (_: Throwable) {
            java.setExecutable(true, false)
        }
    }

    private fun findRuntimeJava(runtime: File): File? =
        File(runtime, "bin/java").takeIf { it.isFile }
            ?: runtime.walkTopDown().firstOrNull { it.isFile && it.path.endsWith("/bin/java") }

    private fun extractTar(input: InputStream, target: File) {
        TarArchiveInputStream(input).use { tar ->
            var entry = tar.nextTarEntry
            while (entry != null) {
                val safe = safeChild(target, entry.name)
                if (entry.isDirectory) {
                    safe.mkdirs()
                } else {
                    safe.parentFile?.mkdirs()
                    FileOutputStream(safe).use { out -> tar.copyTo(out) }
                    safe.setExecutable(entry.name.endsWith("/java") || entry.name.endsWith("/javac") || entry.name.contains("/bin/"))
                }
                entry = tar.nextTarEntry
            }
        }
    }

    private fun extractZip(input: InputStream, target: File) {
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val safe = safeChild(target, entry.name)
                if (entry.isDirectory) safe.mkdirs()
                else {
                    safe.parentFile?.mkdirs()
                    FileOutputStream(safe).use { out -> zip.copyTo(out) }
                    if (entry.name.contains("/bin/")) safe.setExecutable(true)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun safeChild(root: File, name: String): File {
        val rootPath = root.canonicalFile
        val child = File(rootPath, name).canonicalFile
        if (child.path != rootPath.path && !child.path.startsWith(rootPath.path + File.separator)) {
            throw IOException("Unsafe runtime archive entry: $name")
        }
        return child
    }

    private fun canBindUdpPort(port: Int): Boolean {
        return try {
            DatagramSocket(port).use { true }
        } catch (_: IOException) {
            false
        }
    }

    private fun download(url: String, out: File) {
        val temp = File(out.parentFile, out.name + ".part")
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            val total = max(c.contentLengthLong, -1L)
            var done = 0L
            var lastEmit = 0L
            temp.delete()
            c.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastEmit >= 200 || (total > 0 && done >= total)) {
                            emit(status = "Downloading Geyser", progress = done, total = total)
                            lastEmit = now
                        }
                    }
                }
            }
            if (temp.length() < 100_000) throw IOException("Downloaded Geyser file is unexpectedly small.")
            if (!temp.renameTo(out)) {
                temp.copyTo(out, overwrite = true)
                temp.delete()
            }
        } finally {
            c.disconnect()
        }
    }

    private fun emit(
        status: String? = null,
        log: String? = null,
        progress: Long = -1,
        total: Long = -1,
        running: Boolean? = null,
        error: String? = null
    ) {
        val i = Intent(ACTION_UPDATE).setPackage(packageName)
        if (status != null) i.putExtra(EXTRA_STATUS, status)
        if (log != null) i.putExtra(EXTRA_LOG, log)
        if (progress >= 0) i.putExtra(EXTRA_PROGRESS, progress)
        if (total >= 0) i.putExtra(EXTRA_TOTAL, total)
        if (running != null) i.putExtra(EXTRA_RUNNING, running)
        if (error != null) i.putExtra(EXTRA_ERROR, error)
        sendBroadcast(i)
        updateNotification(status ?: "Geyser Mobile", log ?: "")
    }

    private fun updateNotification(title: String, text: String) {
        val notification = Notification.Builder(this, "geyser")
            .setContentTitle(title)
            .setContentText(text.take(100))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(7, notification)
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"
        val kb = value / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the foreground server alive when the launcher activity is swiped away.
        // The service is intentionally independent from the UI process.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopRequested = true
        setServiceRunning(false)
        worker?.interrupt()
        super.onDestroy()
        // Geyser/Java is hosted by this dedicated :geyser Android process.
        // Kill only this service process so the embedded JVM and UDP 19132 socket
        // are released immediately; the main UI process is unaffected.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
