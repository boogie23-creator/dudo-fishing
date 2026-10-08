package com.flowervillage.twins

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * 세은이·아인이 도트마을: 웹 게임을 Android 앱 내부에서 오프라인 실행합니다.
 * 웹 배포(docs/index.html)와 Android 앱(assets/index.html)은 동일한 게임입니다.
 */
class MainActivity : Activity() {
    private lateinit var gameView: WebView
    private val startUrl = "https://appassets.androidplatform.net/assets/index.html"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(65, 108, 83)
        window.navigationBarColor = Color.rgb(249, 247, 226)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        gameView = WebView(this).apply {
            setBackgroundColor(Color.rgb(249, 247, 226))
            settings.apply {
                javaScriptEnabled = true // 내부에 포함된 게임의 JavaScript에만 사용
                domStorageEnabled = true // 게임 진행 상황 저장(localStorage)
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportZoom(false)
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?, request: WebResourceRequest?
                ): WebResourceResponse? {
                    return request?.url?.let { assetLoader.shouldInterceptRequest(it) }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest?
                ): Boolean {
                    // 외부 웹페이지로의 자동 이동은 차단합니다.
                    return request?.url?.host != "appassets.androidplatform.net"
                }
            }
        }
        setContentView(gameView)
        if (savedInstanceState == null) gameView.loadUrl(startUrl)
        else gameView.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        gameView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Handled to return to previous game page if available")
    override fun onBackPressed() {
        if (gameView.canGoBack()) gameView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        gameView.destroy()
        super.onDestroy()
    }
}
