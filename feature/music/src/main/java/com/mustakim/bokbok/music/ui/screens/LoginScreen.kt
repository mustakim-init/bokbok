package com.mustakim.bokbok.music.ui.screens
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import com.mustakim.bokbok.music.utils.putLegacyPoToken
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.music.constants.AccountChannelHandleKey
import com.mustakim.bokbok.music.constants.AccountEmailKey
import com.mustakim.bokbok.music.constants.AccountNameKey
import com.mustakim.bokbok.music.constants.DataSyncIdKey
import com.mustakim.bokbok.music.constants.InnerTubeCookieKey
import com.mustakim.bokbok.music.constants.VisitorDataKey
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.data.local.PreferenceStore
import com.mustakim.bokbok.data.local.dataStore
import com.mustakim.bokbok.music.utils.putLegacyPoToken
import com.mustakim.bokbok.data.local.rememberPreference
import com.mustakim.bokbok.music.utils.reportException
import com.mustakim.bokbok.music.innertube.YouTube
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch

const val LOGIN_ROUTE = "music_login"
const val LOGIN_URL_ARGUMENT = "url"

fun buildLoginRoute(startUrl: String? = null): String {
    val resolvedUrl = startUrl?.trim().takeUnless { it.isNullOrBlank() } ?: return LOGIN_ROUTE
    return "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT=${Uri.encode(resolvedUrl)}"
}

private const val DEFAULT_LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, DelicateCoroutinesApi::class)
@Composable
fun LoginScreen(
    navController: NavController,
    startUrl: String? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")

    var webView: WebView? = null

    AndroidView(
        modifier = Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        val isYouTubePage = url?.contains("youtube.com", ignoreCase = true) == true
                        if (isYouTubePage) {
                            loadUrl("javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var v=c.get('VISITOR_DATA');if(v){Android.onRetrieveVisitorData(v);return}}var y=window.yt&&window.yt.config_;if(y&&y.VISITOR_DATA){Android.onRetrieveVisitorData(y.VISITOR_DATA);return}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"VISITOR_DATA\":\"([^\"]+)\"/);if(m){Android.onRetrieveVisitorData(m[1]);return}}}catch(e){}})())")
                            loadUrl("javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var d=c.get('DATASYNC_ID');if(d){Android.onRetrieveDataSyncId(d);return}}var y=window.yt&&window.yt.config_;if(y&&y.DATASYNC_ID){Android.onRetrieveDataSyncId(y.DATASYNC_ID);return}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"DATASYNC_ID\":\"([^\"]+)\"/);if(m){Android.onRetrieveDataSyncId(m[1]);return}}}catch(e){}})())")
                            loadUrl("javascript:void((function(){try{var c=window.ytcfg;if(c&&c.get){var t=c.get('PO_TOKEN');if(t){Android.onRetrievePoToken(t);return}}var s=document.querySelectorAll('script');for(var i=0;i<s.length;i++){var m=s[i].textContent.match(/\"PO_TOKEN\":\"([^\"]+)\"/);if(m){Android.onRetrievePoToken(m[1]);return}}}catch(e){}})())")
                        }

                        val mergedCookie = mergeYouTubeCookies(cookieManager)
                        if (!mergedCookie.isNullOrBlank()) {
                            innerTubeCookie = mergedCookie
                            coroutineScope.launch {
                                YouTube.accountInfo().onSuccess {
                                    accountName = it.name
                                    accountEmail = it.email.orEmpty()
                                    accountChannelHandle = it.channelHandle.orEmpty()
                                }.onFailure {
                                    reportException(it)
                                }
                            }
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRetrieveVisitorData(newVisitorData: String?) {
                        if (!newVisitorData.isNullOrBlank()) {
                            visitorData = newVisitorData
                        }
                    }
                    @JavascriptInterface
                    fun onRetrieveDataSyncId(newDataSyncId: String?) {
                        if (!newDataSyncId.isNullOrBlank()) {
                            dataSyncId = newDataSyncId
                        }
                    }
                    @JavascriptInterface
                    fun onRetrievePoToken(newPoToken: String?) {
                        if (!newPoToken.isNullOrBlank()) {
                            PreferenceStore.launchEdit(context.dataStore) {
                                putLegacyPoToken(newPoToken)
                            }
                        }
                    }
                }, "Android")
                webView = this
                loadUrl(startUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_LOGIN_URL)
            }
        }
    )

    TopAppBar(
        title = { Text(stringResource(CoreR.string.login)) },
        navigationIcon = {
            BokBokIconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(CoreR.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}

private fun mergeYouTubeCookies(cookieManager: CookieManager): String? {
    val cookieParts = linkedMapOf<String, String>()

    listOf(
        "https://music.youtube.com",
        "https://www.youtube.com",
        "https://youtube.com",
    ).forEach { url ->
        cookieManager.getCookie(url)
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separatorIndex = part.indexOf('=')
                if (separatorIndex <= 0) return@forEach

                val key = part.substring(0, separatorIndex).trim()
                val value = part.substring(separatorIndex + 1).trim()
                if (key.isNotEmpty()) {
                    cookieParts[key] = value
                }
            }
    }

    return cookieParts.takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(separator = "; ") { (key, value) -> "$key=$value" }
}
