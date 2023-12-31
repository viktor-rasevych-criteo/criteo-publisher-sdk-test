/*
 *    Copyright 2020 Criteo
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.criteo.publisher.adview

import android.content.ComponentName
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import com.criteo.publisher.DependencyProvider
import com.criteo.publisher.annotation.OpenForTesting

@OpenForTesting
internal class AdWebViewClient(
    private val listener: RedirectionListener,
    private val hostActivityName: ComponentName?
) : WebViewClient() {

  private val redirection: Redirection = DependencyProvider.getInstance().provideRedirection()
  private var adWebViewClientListener: AdWebViewClientListener? = null

  fun setAdWebViewClientListener(listener: AdWebViewClientListener) {
    adWebViewClientListener = listener
  }

  fun open(url: String) {
    openUrl(url)
  }

  override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
    openUrl(url)
    return true
  }

  override fun shouldInterceptRequest(view: WebView, url: String?): WebResourceResponse? {
    return adWebViewClientListener?.shouldInterceptRequest(url.orEmpty())
  }

  @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
  override fun shouldInterceptRequest(
      view: WebView,
      request: WebResourceRequest?
  ): WebResourceResponse? {
    val url = request?.url?.toString().orEmpty()
    return adWebViewClientListener?.shouldInterceptRequest(url)
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    adWebViewClientListener?.onPageFinished()
  }

  private fun openUrl(url: String?) {
    redirection.redirect(url.orEmpty(), hostActivityName, object : RedirectionListener {
      override fun onUserRedirectedToAd() {
        listener.onUserRedirectedToAd()
      }

      override fun onRedirectionFailed() {
        listener.onRedirectionFailed()
        adWebViewClientListener?.onOpenFailed()
      }

      override fun onUserBackFromAd() {
        listener.onUserBackFromAd()
      }
    })
  }
}
