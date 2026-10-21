package com.veoturbo.capture

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlField: EditText
    private lateinit var counter: TextView
    private val entries = Collections.synchronizedList(mutableListOf<JSONObject>())
    private val natives = Collections.synchronizedList(mutableListOf<JSONObject>())
    private var js = ""
    private var docStart = false

    private fun iso() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun updateCounter() {
        counter.text = "Requêtes JS : ${entries.size} • Ressources : ${natives.size}"
    }

    private fun load(raw: String) {
        var u = raw.trim()
        if (u.isEmpty()) return
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        getPreferences(MODE_PRIVATE).edit().putString("url", u).apply()
        web.loadUrl(u)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        js = assets.open("capture.js").bufferedReader().readText()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        urlField = EditText(this).apply {
            hint = "https://adresse-de-l-application"
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO
            setText(getPreferences(MODE_PRIVATE).getString("url", ""))
            setOnEditorActionListener { _, _, _ -> load(text.toString()); true }
        }
        val go = Button(this).apply { text = "OK"; setOnClickListener { load(urlField.text.toString()) } }
        top.addView(urlField, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(go)

        web = WebView(this)

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(4)); setBackgroundColor(Color.parseColor("#EEEEEE")) }
        counter = TextView(this).apply { textSize = 13f }
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val export = Button(this).apply { text = "Exporter"; setOnClickListener { startExport() } }
        val clear = Button(this).apply {
            text = "Vider"
            setOnClickListener { entries.clear(); natives.clear(); updateCounter() }
        }
        btns.addView(export, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        btns.addView(clear, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(counter)
        bottom.addView(btns)

        root.addView(top)
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottom)
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
        }
        CookieManager.getInstance().apply { setAcceptCookie(true); setAcceptThirdPartyCookies(web, true) }

        web.addJavascriptInterface(Bridge(), "VTBridge")
        docStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (docStart) WebViewCompat.addDocumentStartJavaScript(web, js, setOf("*"))

        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, r: WebResourceRequest): WebResourceResponse? {
                if (natives.size < 20000) {
                    try {
                        natives.add(JSONObject().put("type", "native").put("time", iso())
                            .put("method", r.method).put("url", r.url.toString())
                            .put("mainFrame", r.isForMainFrame)
                            .put("headers", JSONObject(r.requestHeaders as Map<*, *>)))
                    } catch (_: Exception) {}
                }
                return null
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                if (!docStart) view.evaluateJavascript(js, null)
                if (url != null) urlField.setText(url)
            }
            override fun onPageFinished(view: WebView, url: String?) { updateCounter() }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
        })

        updateCounter()
        val saved = urlField.text.toString()
        if (saved.isNotBlank()) load(saved)
    }

    private fun startExport() {
        Toast.makeText(this, "Collecte en cours, patientez…", Toast.LENGTH_SHORT).show()
        web.evaluateJavascript("window.__vt_export ? (window.__vt_export(), 'ok') : 'absent'") { r ->
            if (r?.contains("absent") == true) Toast.makeText(this, "Ouvrez d'abord une page", Toast.LENGTH_LONG).show()
        }
    }

    inner class Bridge {
        @JavascriptInterface
        fun entry(json: String) {
            try { entries.add(JSONObject(json)); runOnUiThread { updateCounter() } } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun finish(pageJson: String) {
            Thread {
                try {
                    val nat = JSONArray(natives.toList())
                    val hosts = TreeSet<String>()
                    for (i in 0 until nat.length()) {
                        android.net.Uri.parse(nat.getJSONObject(i).getString("url")).host?.let { hosts.add(it) }
                    }
                    val cookies = JSONObject()
                    hosts.forEach { h -> CookieManager.getInstance().getCookie("https://$h")?.let { cookies.put(h, it) } }

                    val rootJson = JSONObject()
                        .put("meta", JSONObject().put("tool", "Veo Turbo Capture").put("date", iso())
                            .put("device", Build.MODEL).put("android", Build.VERSION.RELEASE))
                        .put("page", JSONObject(pageJson))
                        .put("cookies", cookies)
                        .put("entries", JSONArray(entries.toList()))
                        .put("native", nat)

                    val name = "capture_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json"
                    val cv = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, "application/json")
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/VeoTurbo")
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
                    contentResolver.openOutputStream(uri)!!.use { it.write(rootJson.toString().toByteArray()) }

                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Enregistré : Download/VeoTurbo/$name", Toast.LENGTH_LONG).show()
                        val s = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(s, "Partager la capture"))
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this@MainActivity, "Erreur export : ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }
    }
}
