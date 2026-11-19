package com.softm.sangsokhannun

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.JavaScriptReplyProxy
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import org.json.JSONObject
import softm.hannun.ads.HannunAdConfig
import softm.hannun.ads.HannunAds
import softm.hannun.ads.HannunWebViewAds
import softm.hannun.update.HannunInAppUpdateManager

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var ads: HannunAds
    private lateinit var updater: HannunInAppUpdateManager
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: LinearLayout
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingExport: ByteArray? = null
    private var pageReady = false
    private var pageSensitive = false
    private var returningFromPrint = false
    private var pageRevision = 0
    private var pendingImport: Pair<JavaScriptReplyProxy, Int>? = null
    private val fileExecutor = Executors.newSingleThreadExecutor()

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pending = pendingImport
        pendingImport = null
        if (pending != null) {
            if (uri == null) replyImport(pending, JSONObject().put("type", "backupCancelled"))
            else fileExecutor.execute {
                val response = runCatching {
                    val content = contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(MAX_BACKUP_BYTES + 1)
                        var total = 0
                        while (total < buffer.size) {
                            val count = input.read(buffer, total, buffer.size - total)
                            if (count < 0) break
                            require(count > 0)
                            total += count
                        }
                        require(total in 1..MAX_BACKUP_BYTES)
                        String(buffer, 0, total, Charsets.UTF_8).also { buffer.fill(0) }
                    } ?: error("Cannot read selection")
                    validateEnvelope(content)
                    JSONObject().put("type", "backupSelected").put("content", content)
                }.getOrElse {
                    JSONObject().put("type", "backupError")
                        .put("message", "2MB 이하의 상속한눈 암호화 백업 파일을 선택해 주세요.")
                }
                runOnUiThread { replyImport(pending, response) }
            }
        }
    }

    private val openFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback
        fileCallback = null
        callback?.onReceiveValue(
            if (result.resultCode == RESULT_OK) WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            else null
        )
    }
    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val bytes = pendingExport
        pendingExport = null
        if (uri != null && bytes != null) {
            fileExecutor.execute {
                val result = runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        ?: error("Cannot open destination")
                }
                bytes.fill(0)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) toast(
                        if (result.isSuccess) "암호화 백업을 저장했습니다."
                        else "백업을 저장하지 못했습니다. 다시 시도해 주세요."
                    )
                }
            }
        } else bytes?.fill(0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(8), 0)
        }
        toolbar.addView(TextView(this).apply {
            text = "상속한눈"
            textSize = 18f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(11, 37, 64))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        val menuButton = Button(this).apply {
            text = "앱 메뉴"
            textSize = 14f
            contentDescription = "업데이트, 광고 개인정보 설정, 인쇄"
            setOnClickListener { showMenu(this) }
        }
        toolbar.addView(menuButton, LinearLayout.LayoutParams(-2, dp(48)))
        root.addView(toolbar)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        val webHost = FrameLayout(this)
        web = WebView(this)
        webHost.addView(web, FrameLayout.LayoutParams(-1, -1))
        errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.WHITE)
            visibility = View.GONE
            addView(TextView(context).apply {
                text = "상속한눈에 연결하지 못했습니다.\n인터넷 연결을 확인하고 다시 시도해 주세요."
                textSize = 17f
                gravity = Gravity.CENTER
            })
            addView(Button(context).apply {
                text = "다시 연결"
                setOnClickListener { web.loadUrl(BuildConfig.WEB_URL) }
            })
        }
        webHost.addView(errorPanel, FrameLayout.LayoutParams(-1, -1))
        root.addView(webHost, LinearLayout.LayoutParams(-1, 0, 1f))
        val adHost = LinearLayout(this)
        root.addView(adHost, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        configureWeb()
        ads = HannunAds(this, adHost, HannunAdConfig(
            BuildConfig.ADMOB_NATIVE_ID, BuildConfig.ADMOB_BANNER_ID,
            BuildConfig.ADMOB_INTERSTITIAL_ID, BuildConfig.ADMOB_EXIT_ID
        ))
        updater = HannunInAppUpdateManager(this) { /* Native library owns update prompts. */ }
        ads.start()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pageReady && WebPolicy.isTrusted(web.url)) {
                    web.evaluateJavascript(
                        "(function(){var d=document.querySelector('dialog[open]');if(!d)return false;" +
                            "var e=new Event('cancel',{cancelable:true});if(d.dispatchEvent(e))d.close();return true;})();"
                    ) { handled ->
                        if (handled != "true" && !isFinishing && !isDestroyed) navigateBack()
                    }
                } else navigateBack()
            }
        })
        web.loadUrl(BuildConfig.WEB_URL)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWeb() {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            if (android.os.Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        HannunWebViewAds.markNativeAds(web.settings)
        web.settings.userAgentString += " SangsokHannun/" + BuildConfig.VERSION_NAME
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (WebPolicy.isTrusted(url)) return false
                if (request.isForMainFrame && request.hasGesture() && WebPolicy.isExternalHttps(url)) openExternal(url)
                return true
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (WebPolicy.isAdFit(request.url.toString())) {
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                pageRevision++
                pendingImport = null
                pageReady = false
                pageSensitive = false
                progress.visibility = View.VISIBLE
                errorPanel.visibility = View.GONE
                if (::ads.isInitialized) ads.setSuppressed(true)
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (!WebPolicy.isTrusted(url)) return
                pageReady = errorPanel.visibility != View.VISIBLE
                progress.visibility = View.GONE
                view.evaluateJavascript(
                    "(function(){if(!document.getElementById('hannun-native-ad-style')){var s=document.createElement('style');s.id='hannun-native-ad-style';s.textContent='ins.kakao_ad_area,[data-adfit],.adfit-container{display:none!important;height:0!important;margin:0!important;padding:0!important;}';document.head.appendChild(s);}document.querySelectorAll('ins.kakao_ad_area,[data-adfit],.adfit-container').forEach(function(e){e.remove();});})();",
                    null
                )
                if (::ads.isInitialized && pageReady) ads.setSuppressed(pageSensitive)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showLoadError()
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) showLoadError()
            }
            // SSL errors use WebView's default cancel behaviour; they are never bypassed.
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, value: Int) { progress.progress = value }
            override fun onShowFileChooser(
                view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                if (!pageReady || !WebPolicy.isTrusted(view.url)) return false
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
                runCatching { openFile.launch(intent) }.onFailure {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = null
                    toast("파일 선택기를 열 수 없습니다.")
                }
                return true
            }
        }
        web.setDownloadListener { _, _, _, _, _ ->
            toast("설정의 암호화 백업 기능으로 파일을 저장해 주세요.")
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "HannunNative", setOf(WebPolicy.ORIGIN)) {
                    view, message, sourceOrigin, isMainFrame, replyProxy ->
                if (isMainFrame && sourceOrigin.toString().trimEnd('/') == WebPolicy.ORIGIN &&
                    WebPolicy.isTrusted(view.url)) handleNativeMessage(message.data, replyProxy)
            }
        }
    }

    private fun handleNativeMessage(raw: String?, replyProxy: JavaScriptReplyProxy) {
        if (raw == null || raw.length > MAX_MESSAGE_CHARS) return
        runCatching {
            val message = JSONObject(raw)
            when (message.optString("type")) {
                "sensitive" -> if (message.has("value") && message.get("value") is Boolean) {
                    pageSensitive = message.getBoolean("value")
                    ads.setSuppressed(!pageReady || pageSensitive)
                }
                "print" -> if (pageReady) printReport()
                "exportBackup" -> if (pageReady) prepareExport(message)
                "importBackup" -> if (pageReady && pendingImport == null) {
                    pendingImport = replyProxy to pageRevision
                    runCatching {
                        importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    }.onFailure {
                        pendingImport = null
                        replyProxy.postMessage(JSONObject().put("type", "backupError")
                            .put("message", "파일 선택기를 열 수 없습니다.").toString())
                    }
                }
            }
        }.onFailure {
            // Never log message contents; they can contain encrypted personal records.
            toast("요청을 처리할 수 없습니다.")
        }
    }
    private fun prepareExport(message: JSONObject) {
        if (pendingExport != null) return
        val content = message.optString("content")
        val bytes = content.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= MAX_BACKUP_BYTES)
        validateEnvelope(content)
        pendingExport = bytes
        saveFile.launch("sangsokhannun-backup.json")
    }
    private fun validateEnvelope(content: String) {
        require(content.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES)
        val envelope = JSONObject(content)
        require(envelope.keys().asSequence().toSet() == setOf("application", "version", "kdf", "iterations", "cipher", "salt", "iv", "data"))
        require(!envelope.has("assets") && !envelope.has("debts") && !envelope.has("case"))
        require(envelope.optString("application") == "sangsokhannun" && envelope.optInt("version") == 1)
        require(envelope.optString("kdf") == "PBKDF2-SHA256" && envelope.optInt("iterations") == 310000)
        require(envelope.optString("cipher") == "AES-GCM")
        require(listOf("salt", "iv", "data").all { envelope.get(it) is String })
        require(envelope.getString("data").matches(Regex("[A-Za-z0-9+/]+={0,2}")))
        require(android.util.Base64.decode(envelope.getString("data"), android.util.Base64.DEFAULT).size >= 16)
        require(android.util.Base64.decode(envelope.getString("salt"), android.util.Base64.DEFAULT).size == 16)
        require(android.util.Base64.decode(envelope.getString("iv"), android.util.Base64.DEFAULT).size == 12)
    }
    private fun replyImport(pending: Pair<JavaScriptReplyProxy, Int>, response: JSONObject) {
        if (!isFinishing && !isDestroyed && pending.second == pageRevision &&
            pageReady && WebPolicy.isTrusted(web.url)) {
            runCatching { pending.first.postMessage(response.toString()) }
        }
    }
    private fun navigateBack() {
        if (web.canGoBack()) web.goBack() else ads.showExitConfirmation { finish() }
    }
    private fun printReport() {
        if (!pageReady || !WebPolicy.isTrusted(web.url)) return
        returningFromPrint = true
        ads.setSuppressed(true)
        val manager = getSystemService(PRINT_SERVICE) as PrintManager
        manager.print("상속한눈 상담 준비자료", web.createPrintDocumentAdapter("상속한눈 상담 준비자료"),
            PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
    }
    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "업데이트 확인")
        popup.menu.add(0, 2, 1, "광고 개인정보 설정")
        popup.menu.add(0, 3, 2, "상담 자료 인쇄 · PDF")
        popup.menu.add(0, 4, 3, "개인정보 안내")
        popup.menu.add(0, 5, 4, "브라우저에서 열기")
        popup.menu.add(0, 6, 5, "앱 정보")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> updater.checkManually { state ->
                    if (state.optString("status") == "not_play_install")
                        toast("Google Play 설치본에서 업데이트를 확인할 수 있습니다.")
                    else if (state.optString("status") == "up_to_date") toast("최신 버전입니다.")
                }
                2 -> ads.showPrivacyOptions()
                3 -> printReport()
                4 -> openExternal(BuildConfig.WEB_URL + "privacy.html")
                5 -> openExternal(BuildConfig.WEB_URL)
                6 -> AlertDialog.Builder(this)
                    .setTitle("상속한눈 " + BuildConfig.VERSION_NAME)
                    .setMessage(if (BuildConfig.DEBUG) "개발 검증용 앱 · Google 테스트 광고 사용" else "상속 준비와 절차 관리")
                    .setPositiveButton("확인", null).show()
            }
            true
        }
        popup.show()
    }
    private fun openExternal(url: String) {
        if (!WebPolicy.isExternalHttps(url)) return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
            .onFailure { toast("링크를 열 앱이 없습니다.") }
    }
    private fun showLoadError() {
        pageReady = false
        progress.visibility = View.GONE
        errorPanel.visibility = View.VISIBLE
        if (::ads.isInitialized) ads.setSuppressed(true)
    }
    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
        if (returningFromPrint && pageReady && WebPolicy.isTrusted(web.url)) {
            returningFromPrint = false
            web.evaluateJavascript("window.dispatchEvent(new Event('afterprint'));", null)
        }
        if (::ads.isInitialized) ads.onResume()
        if (::updater.isInitialized) updater.onResume()
    }
    override fun onPause() {
        if (::updater.isInitialized) updater.onPause()
        if (::ads.isInitialized) ads.onPause()
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }
    @Deprecated("Shared Google Play library uses the Activity result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (::updater.isInitialized && updater.onActivityResult(requestCode, resultCode)) return
        super.onActivityResult(requestCode, resultCode, data)
    }
    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        pendingExport?.fill(0)
        pendingExport = null
        pendingImport = null
        fileExecutor.shutdownNow()
        if (::updater.isInitialized) updater.destroy()
        if (::ads.isInitialized) ads.destroy()
        if (::web.isInitialized) {
            (web.parent as? FrameLayout)?.removeView(web)
            web.stopLoading()
            web.destroy()
        }
        super.onDestroy()
    }
    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    companion object {
        private const val MAX_BACKUP_BYTES = 2 * 1024 * 1024
        private const val MAX_MESSAGE_CHARS = 3 * 1024 * 1024
    }
}
