package com.criteo.publisher.advancednative

import android.content.ComponentName
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.criteo.publisher.activity.TopActivityFinder
import com.criteo.publisher.adview.Redirection
import com.criteo.publisher.mock.MockBean
import com.criteo.publisher.mock.MockedDependenciesRule
import com.criteo.publisher.mock.SpyBean
import com.criteo.publisher.model.nativeads.NativeAssets
import com.criteo.publisher.model.nativeads.NativeProduct
import com.criteo.publisher.network.PubSdkApi
import com.criteo.publisher.util.RunOnUiThreadExecutor
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import java.lang.ref.WeakReference
import java.net.URI
import javax.inject.Inject

class NativeAdMapperTest {

  @Rule
  @JvmField
  val mockedDependenciesRule = MockedDependenciesRule()

  @SpyBean
  private lateinit var runOnUiThreadExecutor: RunOnUiThreadExecutor

  @MockBean
  private lateinit var visibilityTracker: VisibilityTracker

  @MockBean
  private lateinit var clickDetection: ClickDetection

  @MockBean
  private lateinit var topActivityFinder: TopActivityFinder

  @MockBean
  private lateinit var redirection: Redirection

  @MockBean
  private lateinit var adChoiceOverlay: AdChoiceOverlay

  @MockBean
  private lateinit var api: PubSdkApi

  @Inject
  private lateinit var mapper: NativeAdMapper

  @Test
  fun map_GivenAssets_ReturnsNativeAdWithSameData() {
    val product = mock<NativeProduct>() {
      on { title } doReturn "myTitle"
      on { description } doReturn "myDescription"
      on { price } doReturn "42€"
      on { callToAction } doReturn "myCTA"
      on { imageUrl } doReturn URI.create("http://click.url").toURL()
    }

    val assets = mock<NativeAssets>() {
      on { this.product } doReturn product
      on { advertiserDomain } doReturn "advDomain"
      on { advertiserDescription } doReturn "advDescription"
      on { advertiserLogoUrl } doReturn URI.create("http://logo.url").toURL()
    }

    val nativeAd = mapper.map(assets, WeakReference(null), mock())

    assertThat(nativeAd.title).isEqualTo("myTitle")
    assertThat(nativeAd.description).isEqualTo("myDescription")
    assertThat(nativeAd.price).isEqualTo("42€")
    assertThat(nativeAd.callToAction).isEqualTo("myCTA")
    assertThat(nativeAd.productImageUrl).isEqualTo(URI.create("http://click.url").toURL())
    assertThat(nativeAd.advertiserDomain).isEqualTo("advDomain")
    assertThat(nativeAd.advertiserDescription).isEqualTo("advDescription")
    assertThat(nativeAd.advertiserLogoImageUrl).isEqualTo(URI.create("http://logo.url").toURL())
  }

  @Test
  fun watchForImpression_GivenVisibilityTriggeredManyTimesOnDifferentViews_NotifyListenerOnceForImpressionAndFirePixels() {
    val listener = mock<CriteoNativeAdListener>()

    val pixel1 = URI.create("http://pixel1.url").toURL()
    val pixel2 = URI.create("http://pixel2.url").toURL()
    val assets = mock<NativeAssets>(defaultAnswer=Answers.RETURNS_DEEP_STUBS) {
      on { impressionPixels } doReturn listOf(pixel1, pixel2)
    }

    val view1 = mock<View>()
    val view2 = mock<View>()

    givenDirectUiExecutor()

    //when
    val nativeAd = mapper.map(assets, WeakReference(listener), mock())

    nativeAd.watchForImpression(view1)
    verify(visibilityTracker).watch(eq(view1), check {
      it.onVisible()
      mockedDependenciesRule.waitForIdleState()
      it.onVisible()
      mockedDependenciesRule.waitForIdleState()
    })

    nativeAd.watchForImpression(view2)
    verify(visibilityTracker).watch(eq(view2), check {
      it.onVisible()
      mockedDependenciesRule.waitForIdleState()
      it.onVisible()
      mockedDependenciesRule.waitForIdleState()
    })

    // then
    verify(listener, times(1)).onAdImpression()
    verify(api, times(1)).executeRawGet(pixel1)
    verify(api, times(1)).executeRawGet(pixel2)
  }

  @Test
  fun setProductClickableView_GivenDifferentViewsClickedManyTimes_NotifyListenerForClicksAndRedirectUser() {
    val listener = mock<CriteoNativeAdListener>()

    val product = mock<NativeProduct> {
      on { clickUrl } doReturn URI.create("click://uri.com")
    }

    val assets = mock<NativeAssets> {
      on { this.product } doReturn product
    }

    val view1 = mock<View>()
    val view2 = mock<View>()

    val topActivity = mock<ComponentName>()
    topActivityFinder.stub {
      on { topActivityName } doReturn topActivity
    }

    givenDirectUiExecutor()

    //when
    val nativeAd = mapper.map(assets, WeakReference(listener), mock())
    nativeAd.setProductClickableView(view1)
    nativeAd.setProductClickableView(view2)

    argumentCaptor<NativeViewClickHandler>().apply {
      verify(clickDetection, times(2)).watch(any(), capture())

      allValues.forEach {
        it.onClick()
        it.onClick()
      }
    }

    // then
    verify(listener, times(4)).onAdClicked()
    verify(redirection, times(4)).redirect(eq("click://uri.com"), eq(topActivity), any())
  }

  @Test
  fun setPrivacyOptOutClickableView_GivenDifferentViewsClickedManyTimes_NotifyListenerForClicksAndRedirectUser() {
    val listener = mock<CriteoNativeAdListener>()

    val assets = mock<NativeAssets>(defaultAnswer=Answers.RETURNS_DEEP_STUBS) {
      on { privacyOptOutClickUrl } doReturn URI.create("privacy://criteo")
    }

    val view1 = mock<View>()
    val view2 = mock<View>()

    val topActivity = mock<ComponentName>()
    topActivityFinder.stub {
      on { topActivityName } doReturn topActivity
    }

    givenDirectUiExecutor()

    //when
    val nativeAd = mapper.map(assets, WeakReference(listener), mock())
    nativeAd.setAdChoiceClickableView(view1)
    nativeAd.setAdChoiceClickableView(view2)

    argumentCaptor<NativeViewClickHandler>().apply {
      verify(clickDetection, times(2)).watch(any(), capture())

      allValues.forEach {
        it.onClick()
        it.onClick()
      }
    }

    // then
    verify(listener, never()).onAdClicked()
    verify(redirection, times(4)).redirect(eq("privacy://criteo"), eq(topActivity), any())
  }

  @Test
  fun addAdChoiceOverlay_GivenAnyView_DelegateToAdChoiceOverlay() {
    val listener = mock<CriteoNativeAdListener>()
    val nativeAssets = mock<NativeAssets>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    val inputView = mock<View>()
    val overlappedView = mock<ViewGroup>()

    whenever(adChoiceOverlay.addOverlay(inputView)).doReturn(overlappedView)

    val nativeAd = mapper.map(nativeAssets, WeakReference(listener), mock())
    val outputView = nativeAd.addAdChoiceOverlay(inputView)

    assertThat(outputView).isEqualTo(overlappedView)
  }

  @Test
  fun getAdChoiceView_GivenAnyView_DelegateToAdChoiceOverlay() {
    val listener = mock<CriteoNativeAdListener>()
    val nativeAssets = mock<NativeAssets>(defaultAnswer = Answers.RETURNS_DEEP_STUBS)
    val inputView = mock<View>()
    val adChoiceView = mock<ImageView>()

    whenever(adChoiceOverlay.getAdChoiceView(inputView)).doReturn(adChoiceView)

    val nativeAd = mapper.map(nativeAssets, WeakReference(listener), mock())
    val outputView = nativeAd.getAdChoiceView(inputView)

    assertThat(outputView).isEqualTo(adChoiceView)
  }

  @Test
  fun createRenderedNativeView_GivenRenderer_InflateThenRenderAndSetupInternals() {
    val listener = mock<CriteoNativeAdListener>()
    val renderer = mock<CriteoNativeRenderer>()
    val assets = mock<NativeAssets>(defaultAnswer=Answers.RETURNS_DEEP_STUBS)
    val context = mock<Context>()
    val parent = mock<ViewGroup>()
    val inflatedView = mock<View>()
    val nativeView = mock<ViewGroup>()
    val adChoiceView = mock<ImageView>()

    whenever(renderer.createNativeView(context, parent)).doReturn(inflatedView)
    whenever(adChoiceOverlay.addOverlay(inflatedView)).doReturn(nativeView)
    whenever(adChoiceOverlay.getAdChoiceView(nativeView)).doReturn(adChoiceView)

    val nativeAd = spy(mapper.map(assets, WeakReference(listener), renderer))
    val renderedView = nativeAd.createNativeRenderedView(context, parent)

    assertThat(renderedView).isEqualTo(nativeView)

    inOrder(renderer, nativeAd) {
      verify(renderer).renderNativeView(nativeView, nativeAd)
      verify(nativeAd).watchForImpression(nativeView)
      verify(nativeAd).setProductClickableView(nativeView)
      verify(nativeAd).setAdChoiceClickableView(adChoiceView)
    }
  }

  private fun givenDirectUiExecutor() {
    runOnUiThreadExecutor.stub {
      on { executeAsync(any()) } doAnswer {
        val command: Runnable = it.getArgument(0)
        command.run()
      }
    }
  }

}