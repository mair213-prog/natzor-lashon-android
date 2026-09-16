package il.co.natzorlashon.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object { @Volatile var isForeground: Boolean = false }
    private lateinit var webView: WebView
    private val appHost = "natzor-lashon.onrender.com"
    private val releasesApi =
        "https://api.github.com/repos/mair213-prog/natzor-lashon-android/releases/latest"

    private var pendingUpdateUrl: String? = null
    private var pendingUpdateVersion: String? = null
    private var downloadId: Long = -1L

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
            if (id != downloadId || id == -1L) return
            installDownloadedApk(id)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        registerDownloadReceiver()

        webView = findViewById(R.id.webView)
        CookieManager.getInstance().setAcceptCookie(true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false
            userAgentString = "$userAgentString NatzorLashonAndroid/${BuildConfig.VERSION_NAME}"
        }

        webView.addJavascriptInterface(NotificationBridge(), "NatzorNative")
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.host == appHost) return false

                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "הקישור החיצוני אינו זמין במכשיר זה",
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                registerFcmTokenWithWeb()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    Toast.makeText(
                        this@MainActivity,
                        "אין כרגע חיבור למערכת. בדוק את חיבור האינטרנט או את אישור הסינון.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        if (savedInstanceState == null) {
            webView.loadUrl("https://natzor-lashon.onrender.com")
        }

        requestNotificationPermissionIfNeeded()
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            getSharedPreferences("natzor_push", MODE_PRIVATE).edit().putString("fcm_token", token).apply()
            registerFcmTokenWithWeb()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        // Google Play builds are updated by Google Play.
        // Direct APK builds check the latest permanent GitHub Release.
        if (BuildConfig.DISTRIBUTION == "direct") {
            checkForDirectUpdate()
        }
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        registerFcmTokenWithWeb()
        if (
            BuildConfig.DISTRIBUTION == "direct" &&
            pendingUpdateUrl != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.canRequestPackageInstalls()
        ) {
            val url = pendingUpdateUrl!!
            val version = pendingUpdateVersion ?: "חדש"
            pendingUpdateUrl = null
            pendingUpdateVersion = null
            downloadAndInstall(url, version)
        }
    }

    override fun onPause() {
        isForeground = false
        super.onPause()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(downloadReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    inner class NotificationBridge {
        @JavascriptInterface
        fun openNotificationSettings() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1201)
                } else {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
            }
        }

        @JavascriptInterface
        fun notificationsEnabled(): Boolean {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return if (Build.VERSION.SDK_INT >= 24) nm.areNotificationsEnabled() else true
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1201)
        }
    }

    private fun registerFcmTokenWithWeb() {
        if (!::webView.isInitialized) return
        val token = getSharedPreferences("natzor_push", MODE_PRIVATE).getString("fcm_token", null) ?: return
        val quoted = JSONObject.quote(token)
        webView.post { webView.evaluateJavascript("if(window.registerNativeFcmToken){window.registerNativeFcmToken($quoted)}", null) }
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(downloadReceiver, filter)
        }
    }

    private fun checkForDirectUpdate() {
        Executors.newSingleThreadExecutor().execute {
            try {
                val conn = (URL(releasesApi).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7000
                    readTimeout = 7000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "NatzorLashonAndroid/${BuildConfig.VERSION_NAME}")
                }

                if (conn.responseCode !in 200..299) return@execute
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(json)
                val latest = obj.optString("tag_name").removePrefix("v").trim()
                if (latest.isBlank() || compareVersions(latest, BuildConfig.VERSION_NAME) <= 0) {
                    return@execute
                }

                val assets = obj.optJSONArray("assets") ?: return@execute
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name") == "Natzor-Lashon-Release.apk") {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                if (apkUrl.isNullOrBlank()) return@execute

                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("עדכון חדש זמין")
                        .setMessage("גרסה $latest של „נצור לשונך” זמינה להתקנה.")
                        .setPositiveButton("עדכן עכשיו") { _, _ ->
                            requestInstallAndDownload(apkUrl, latest)
                        }
                        .setNegativeButton("אחר כך", null)
                        .show()
                }
            } catch (_: Exception) {
                // Update checks must never prevent the app itself from opening.
            }
        }
    }

    private fun requestInstallAndDownload(url: String, version: String) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingUpdateUrl = url
            pendingUpdateVersion = version
            Toast.makeText(
                this,
                "כדי להתקין עדכונים יש לאפשר ל„נצור לשונך” התקנת אפליקציות.",
                Toast.LENGTH_LONG
            ).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        downloadAndInstall(url, version)
    }

    private fun downloadAndInstall(url: String, version: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("עדכון נצור לשונך")
                .setDescription("מוריד גרסה $version")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    "Natzor-Lashon-$version.apk"
                )

            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)
            Toast.makeText(this, "העדכון יורד כעת…", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "לא ניתן היה להתחיל את הורדת העדכון.", Toast.LENGTH_LONG).show()
        }
    }

    private fun installDownloadedApk(id: Long) {
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = dm.query(query)

        cursor.use {
            if (!it.moveToFirst()) return
            val statusIndex = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0 || it.getInt(statusIndex) != DownloadManager.STATUS_SUCCESSFUL) {
                Toast.makeText(this, "הורדת העדכון נכשלה.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val uri = dm.getUriForDownloadedFile(id) ?: run {
            Toast.makeText(this, "קובץ העדכון לא נמצא.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "המכשיר אינו מאפשר כרגע התקנת עדכונים. ייתכן שנדרש אישור מספק הסינון.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val pa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val pb = b.split(".").map { it.toIntOrNull() ?: 0 }
        val size = maxOf(pa.size, pb.size)

        for (i in 0 until size) {
            val av = pa.getOrElse(i) { 0 }
            val bv = pb.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}
