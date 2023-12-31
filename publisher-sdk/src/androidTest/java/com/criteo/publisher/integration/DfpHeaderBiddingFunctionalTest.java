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

package com.criteo.publisher.integration;

import static com.criteo.publisher.CriteoUtil.PROD_CDB_URL;
import static com.criteo.publisher.CriteoUtil.PROD_CP_ID;
import static com.criteo.publisher.StubConstants.STUB_CREATIVE_IMAGE;
import static com.criteo.publisher.StubConstants.STUB_DISPLAY_URL;
import static com.criteo.publisher.StubConstants.STUB_NATIVE_ASSETS;
import static com.criteo.publisher.concurrent.ThreadingUtil.runOnMainThreadAndWait;
import static com.criteo.publisher.view.WebViewLookup.getRootView;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;
import com.criteo.publisher.Criteo;
import com.criteo.publisher.CriteoInitException;
import com.criteo.publisher.CriteoUtil;
import com.criteo.publisher.TestAdUnits;
import com.criteo.publisher.context.ContextData;
import com.criteo.publisher.integration.DfpHeaderBiddingFunctionalTest.DfpSync.SyncInterstitialAdListener;
import com.criteo.publisher.logging.LoggerFactory;
import com.criteo.publisher.mock.MockedDependenciesRule;
import com.criteo.publisher.mock.ResultCaptor;
import com.criteo.publisher.mock.SpyBean;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.model.BannerAdUnit;
import com.criteo.publisher.model.CdbResponse;
import com.criteo.publisher.model.Config;
import com.criteo.publisher.model.InterstitialAdUnit;
import com.criteo.publisher.model.NativeAdUnit;
import com.criteo.publisher.model.nativeads.NativeAssets;
import com.criteo.publisher.model.nativeads.NativeProduct;
import com.criteo.publisher.network.PubSdkApi;
import com.criteo.publisher.test.activity.DummyActivity;
import com.criteo.publisher.util.BuildConfigWrapper;
import com.criteo.publisher.view.WebViewLookup;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.admanager.AdManagerAdRequest.Builder;
import com.google.android.gms.ads.admanager.AdManagerAdView;
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd;
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@FlakyTest(detail = "DFP network is flaky")
public class DfpHeaderBiddingFunctionalTest {

  private static final String MACRO_CPM = "crt_cpm";
  private static final String MACRO_DISPLAY_URL = "crt_displayurl";
  private static final String MACRO_SIZE = "crt_size";
  private static final String MACRO_FORMAT = "crt_format";
  private static final String MACRO_NATIVE_TITLE = "crtn_title";
  private static final String MACRO_NATIVE_DESCRIPTION = "crtn_desc";
  private static final String MACRO_NATIVE_IMAGE = "crtn_imageurl";
  private static final String MACRO_NATIVE_PRICE = "crtn_price";
  private static final String MACRO_NATIVE_CLICK = "crtn_clickurl";
  private static final String MACRO_NATIVE_CTA = "crtn_cta";
  private static final String MACRO_NATIVE_ADVERTISER_NAME = "crtn_advname";
  private static final String MACRO_NATIVE_ADVERTISER_DOMAIN = "crtn_advdomain";
  private static final String MACRO_NATIVE_ADVERTISER_LOGO = "crtn_advlogourl";
  private static final String MACRO_NATIVE_ADVERTISER_CLICK = "crtn_advurl";
  private static final String MACRO_NATIVE_PRIVACY_LINK = "crtn_prurl";
  private static final String MACRO_NATIVE_PRIVACY_IMAGE = "crtn_primageurl";
  private static final String MACRO_NATIVE_PRIVACY_LEGAL = "crtn_prtext";
  private static final String MACRO_NATIVE_PIXEL_COUNT = "crtn_pixcount";
  private static final String MACRO_NATIVE_PIXEL_1 = "crtn_pixurl_0";
  private static final String MACRO_NATIVE_PIXEL_2 = "crtn_pixurl_1";

  private static final String DFP_BANNER_ID = "/140800857/Endeavour_320x50";
  private static final String DFP_INTERSTITIAL_ID = "/140800857/Endeavour_Interstitial_320x480";
  private static final String DFP_NATIVE_ID = "/140800857/Endeavour_Native";

  /**
   * Time (in milliseconds) to wait for the interstitial activity of DFP to open itself after
   * calling {@link AdManagerInterstitialAd#show(Activity)} )}.
   */
  private static final long DFP_INTERSTITIAL_OPENING_TIMEOUT_MS = 1000;

  /**
   * Charset used to encode/decode data with DFP.
   */
  private static final Charset CHARSET = StandardCharsets.UTF_8;

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return Arrays.asList(false, true);
  }

  @Parameter
  public boolean isLiveBiddingEnabled;

  @Rule
  public MockedDependenciesRule mockedDependenciesRule = new MockedDependenciesRule();

  @Rule
  public ActivityTestRule<DummyActivity> activityRule = new ActivityTestRule<>(DummyActivity.class);

  private final BannerAdUnit validBannerAdUnit = TestAdUnits.BANNER_320_50;
  private final BannerAdUnit invalidBannerAdUnit = TestAdUnits.BANNER_UNKNOWN;
  private final BannerAdUnit demoBannerAdUnit = TestAdUnits.BANNER_DEMO;

  private final InterstitialAdUnit validInterstitialAdUnit = TestAdUnits.INTERSTITIAL;
  private final InterstitialAdUnit invalidInterstitialAdUnit = TestAdUnits.INTERSTITIAL_UNKNOWN;
  private final InterstitialAdUnit demoInterstitialAdUnit = TestAdUnits.INTERSTITIAL_DEMO;

  private final NativeAdUnit validNativeAdUnit = TestAdUnits.NATIVE;
  private final NativeAdUnit invalidNativeAdUnit = TestAdUnits.NATIVE_UNKNOWN;

  private final WebViewLookup webViewLookup = new WebViewLookup();

  @SpyBean
  private BuildConfigWrapper buildConfigWrapper;

  @SpyBean
  private PubSdkApi api;

  @SpyBean
  private Config config;

  @Inject
  private Context context;

  @Before
  public void setUp() {
    doReturn(isLiveBiddingEnabled).when(config).isLiveBiddingEnabled();

    RequestConfiguration requestConfiguration = new RequestConfiguration.Builder().setTestDeviceIds(Collections.singletonList(AdRequest.DEVICE_ID_EMULATOR)).build();
    MobileAds.setRequestConfiguration(requestConfiguration);
  }

  @Test
  public void whenGettingBid_GivenValidCpIdAndPrefetchValidBannerId_CriteoMacroAreInjectedInDfpBuilder()
      throws Exception {
    whenGettingBid_GivenValidCpIdAndValidAdUnit_CriteoMacroAreInjectedInDfpBuilder(validBannerAdUnit, false);
  }

  @Test
  public void whenGettingBid_GivenValidCpIdAndPrefetchValidInterstitialId_CriteoMacroAreInjectedInDfpBuilder()
      throws Exception {
    whenGettingBid_GivenValidCpIdAndValidAdUnit_CriteoMacroAreInjectedInDfpBuilder(validInterstitialAdUnit, false);
  }

  @Test
  public void whenGettingBid_GivenValidCpIdAndPrefetchValidInterstitialVideoId_CriteoMacroAreInjectedInDfpBuilder()
      throws Exception {
    whenGettingBid_GivenValidCpIdAndValidAdUnit_CriteoMacroAreInjectedInDfpBuilder(TestAdUnits.INTERSTITIAL_VIDEO, true);
  }

  @Test
  public void whenGettingBid_GivenValidCpIdAndPrefetchValidRewardedVideoId_CriteoMacroAreInjectedInDfpBuilder()
      throws Exception {
    givenInitializedCriteo();

    // this request is ignored because current detected integration (fallback) is not supporting rewarded video
    loadBidAndWait(TestAdUnits.REWARDED, new Builder());
    // now, as a GAM builder was given, then the detected integration is GAM AppBidding and next request will work.

    whenGettingBid_GivenValidCpIdAndValidAdUnit_CriteoMacroAreInjectedInDfpBuilder(TestAdUnits.REWARDED, true);
  }

  private void whenGettingBid_GivenValidCpIdAndValidAdUnit_CriteoMacroAreInjectedInDfpBuilder(AdUnit adUnit, boolean isVideo) throws Exception {
    givenInitializedCriteo(adUnit);

    Builder builder = new Builder();

    loadBidAndWait(adUnit, builder);

    assertCriteoMacroAreInjectedInDfpBuilder(builder, isVideo);
    assertBidRequestHasGoodProfileId();
  }

  @Test
  public void whenGettingBid_GivenValidCpIdAndPrefetchValidNativeId_CriteoNativeMacroAreInjectedInDfpBuilder()
      throws Exception {
    givenInitializedCriteo(validNativeAdUnit);

    Builder builder = new Builder();

    loadBidAndWait(validNativeAdUnit, builder);

    Bundle customTargeting = builder.build().getCustomTargeting();

    // Note: CDB stub does not return any legal text in the payload.
    // But this may be covered by unit tests.
    assertNotNull(customTargeting.getString(MACRO_CPM));
    assertNull(customTargeting.getString(MACRO_DISPLAY_URL));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_TITLE));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_DESCRIPTION));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_IMAGE));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_PRICE));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_CLICK));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_CTA));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_ADVERTISER_NAME));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_ADVERTISER_DOMAIN));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_ADVERTISER_LOGO));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_ADVERTISER_CLICK));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_PRIVACY_LINK));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_PRIVACY_IMAGE));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_PIXEL_1));
    assertNotNull(customTargeting.getString(MACRO_NATIVE_PIXEL_2));
    assertEquals("2", customTargeting.getString(MACRO_NATIVE_PIXEL_COUNT));
    assertEquals(16, customTargeting.size());

    assertBidRequestHasGoodProfileId();
  }

  @Test
  public void whenGettingBid_GivenValidCpIdAndPrefetchInvalidBannerId_CriteoMacroAreNotInjectedInDfpBuilder()
      throws Exception {
    whenGettingBid_GivenValidCpIdAndPrefetchInvalidAdUnit_CriteoMacroAreNotInjectedInDfpBuilder(
        invalidBannerAdUnit);
  }

  @Test
  public void whenGettingBid_GivenValidCpIdAndPrefetchInvalidInterstitialId_CriteoMacroAreNotInjectedInDfpBuilder()
      throws Exception {
    whenGettingBid_GivenValidCpIdAndPrefetchInvalidAdUnit_CriteoMacroAreNotInjectedInDfpBuilder(
        invalidInterstitialAdUnit);
  }

  @Test
  public void whenGettingBid_GivenValidCpIdAndPrefetchInvalidNativeId_CriteoMacroAreNotInjectedInDfpBuilder()
      throws Exception {
    whenGettingBid_GivenValidCpIdAndPrefetchInvalidAdUnit_CriteoMacroAreNotInjectedInDfpBuilder(
        invalidNativeAdUnit);
  }

  private void whenGettingBid_GivenValidCpIdAndPrefetchInvalidAdUnit_CriteoMacroAreNotInjectedInDfpBuilder(
      AdUnit adUnit)
      throws Exception {
    givenInitializedCriteo(adUnit);

    Builder builder = new Builder();

    loadBidAndWait(adUnit, builder);

    Bundle customTargeting = builder.build().getCustomTargeting();

    assertNull(customTargeting.getString(MACRO_CPM));
    assertNull(customTargeting.getString(MACRO_DISPLAY_URL));
    assertNull(customTargeting.getString(MACRO_NATIVE_TITLE));
    assertNull(customTargeting.getString(MACRO_NATIVE_DESCRIPTION));
    assertNull(customTargeting.getString(MACRO_NATIVE_IMAGE));
    assertNull(customTargeting.getString(MACRO_NATIVE_PRICE));
    assertNull(customTargeting.getString(MACRO_NATIVE_CLICK));
    assertNull(customTargeting.getString(MACRO_NATIVE_CTA));
    assertNull(customTargeting.getString(MACRO_NATIVE_ADVERTISER_NAME));
    assertNull(customTargeting.getString(MACRO_NATIVE_ADVERTISER_DOMAIN));
    assertNull(customTargeting.getString(MACRO_NATIVE_ADVERTISER_LOGO));
    assertNull(customTargeting.getString(MACRO_NATIVE_ADVERTISER_CLICK));
    assertNull(customTargeting.getString(MACRO_NATIVE_PRIVACY_LINK));
    assertNull(customTargeting.getString(MACRO_NATIVE_PRIVACY_IMAGE));
    assertNull(customTargeting.getString(MACRO_NATIVE_PRIVACY_LEGAL));
    assertNull(customTargeting.getString(MACRO_NATIVE_PIXEL_1));
    assertNull(customTargeting.getString(MACRO_NATIVE_PIXEL_2));
    assertNull(customTargeting.getString(MACRO_NATIVE_PIXEL_COUNT));
    assertEquals(0, customTargeting.size());
  }

  @Test
  public void whenGettingTestBid_GivenValidCpIdAndPrefetchDemoBannerId_CriteoMacroAreInjectedInDfpBuilder()
      throws Exception {
    givenUsingCdbProd();
    whenGettingTestBid_GivenValidCpIdAndPrefetchDemoAdUnit_CriteoMacroAreInjectedInDfpBuilder(
        demoBannerAdUnit);
  }

  @Test
  public void whenGettingTestBid_GivenValidCpIdAndPrefetchDemoInterstitialId_CriteoMacroAreInjectedInDfpBuilder()
      throws Exception {
    givenUsingCdbProd();
    whenGettingTestBid_GivenValidCpIdAndPrefetchDemoAdUnit_CriteoMacroAreInjectedInDfpBuilder(
        demoInterstitialAdUnit);
  }

  private void whenGettingTestBid_GivenValidCpIdAndPrefetchDemoAdUnit_CriteoMacroAreInjectedInDfpBuilder(
      AdUnit adUnit)
      throws Exception {
    givenInitializedCriteo(adUnit);

    Builder builder = new Builder();

    loadBidAndWait(adUnit, builder);

    assertCriteoMacroAreInjectedInDfpBuilder(builder, false);

    // The amount is not that important, but the format is
    String cpm = builder.build().getCustomTargeting().getString(MACRO_CPM);
    assertEquals("20.00", cpm);
  }

  @Test
  public void whenEnrichingDisplayUrl_GivenValidCpIdAndPrefetchBannerId_DisplayUrlIsEncodedInASpecificManner()
      throws Exception {
    whenEnrichingDisplayUrl_GivenValidCpIdAndPrefetchAdUnit_DisplayUrlIsEncodedInASpecificManner(
        validBannerAdUnit);
  }

  @Test
  public void whenEnrichingDisplayUrl_GivenValidCpIdAndPrefetchInterstitialId_DisplayUrlIsEncodedInASpecificManner()
      throws Exception {
    whenEnrichingDisplayUrl_GivenValidCpIdAndPrefetchAdUnit_DisplayUrlIsEncodedInASpecificManner(
        validInterstitialAdUnit);
  }

  private void whenEnrichingDisplayUrl_GivenValidCpIdAndPrefetchAdUnit_DisplayUrlIsEncodedInASpecificManner(
      AdUnit adUnit)
      throws Exception {
    givenInitializedCriteo(adUnit);

    Builder builder = new Builder();

    loadBidAndWait(adUnit, builder);

    String encodedDisplayUrl = builder.build().getCustomTargeting().getString(MACRO_DISPLAY_URL);
    String decodedDisplayUrl = decodeDfpPayloadComponent(encodedDisplayUrl);

    assertTrue(STUB_DISPLAY_URL.matcher(decodedDisplayUrl).matches());
  }

  @Test
  public void whenEnrichingNativePayload_GivenValidCpIdAndPrefetchNative_PayloadIsEncodedInASpecificManner()
      throws Exception {
    NativeAssets expectedAssets = STUB_NATIVE_ASSETS;
    NativeProduct expectedProduct = expectedAssets.getProduct();

    givenInitializedCriteo(validNativeAdUnit);

    Builder builder = new Builder();

    loadBidAndWait(validNativeAdUnit, builder);

    Bundle bundle = builder.build().getCustomTargeting();

    assertEquals(
        expectedProduct.getTitle(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_TITLE))
    );
    assertEquals(
        expectedProduct.getDescription(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_DESCRIPTION))
    );
    assertEquals(
        expectedProduct.getImageUrl().toString(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_IMAGE))
    );
    assertEquals(
        expectedProduct.getPrice(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_PRICE)));
    assertEquals(expectedProduct.getClickUrl().toString(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_CLICK)));
    assertEquals(expectedProduct.getCallToAction(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_CTA)));
    assertEquals(expectedAssets.getAdvertiserDescription(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_ADVERTISER_NAME)));
    assertEquals(expectedAssets.getAdvertiserDomain(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_ADVERTISER_DOMAIN)));
    assertEquals(expectedAssets.getAdvertiserLogoUrl().toString(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_ADVERTISER_LOGO)));
    assertEquals(expectedAssets.getAdvertiserLogoClickUrl().toString(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_ADVERTISER_CLICK)));
    assertEquals(expectedAssets.getPrivacyOptOutClickUrl().toString(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_PRIVACY_LINK)));
    assertEquals(expectedAssets.getPrivacyOptOutImageUrl().toString(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_PRIVACY_IMAGE)));
    assertEquals(expectedAssets.getImpressionPixels().get(0).toString(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_PIXEL_1)));
    assertEquals(expectedAssets.getImpressionPixels().get(1).toString(),
        decodeDfpPayloadComponent(bundle.getString(MACRO_NATIVE_PIXEL_2)));
    assertEquals(String.valueOf(expectedAssets.getImpressionPixels().size()),
        bundle.getString(MACRO_NATIVE_PIXEL_COUNT));
  }

  private String decodeDfpPayloadComponent(String component) throws Exception {
    // Payload component (such as display URL) should be encoded with:
    // Base64 encoder + URL encoder + URL encoder
    // So we should get back a good component by applying the reverse
    // The reverse: URL decoder + URL decoder + Base64 decoder

    String step1 = URLDecoder.decode(component, CHARSET.name());
    String step2 = URLDecoder.decode(step1, CHARSET.name());
    byte[] step3 = Base64.getDecoder().decode(step2);
    return new String(step3, CHARSET);
  }

  @Test
  public void loadingDfpBanner_GivenValidBanner_DfpViewContainsCreative() throws Exception {
    String html = loadDfpHtmlBanner(validBannerAdUnit);

    assertTrue(html.contains(STUB_CREATIVE_IMAGE));
  }

  @Test
  public void loadingDfpBanner_GivenValidInterstitial_DfpViewContainsCreative() throws Exception {
    String html = loadDfpHtmlInterstitial(validInterstitialAdUnit);

    assertTrue(html.contains(STUB_CREATIVE_IMAGE));
  }

  @Test
  public void loadingDfpBanner_GivenValidNative_DfpViewContainsNativePayload() throws Exception {
    NativeAssets expectedAssets = STUB_NATIVE_ASSETS;
    NativeProduct expectedProduct = expectedAssets.getProduct();

    // URL are encoded because they are given, as query param, to a Google URL that will redirect
    // user.
    // TODO add a test that verify that users are properly redirected to the expected URL.
    String expectedClickUrl =
        URLEncoder.encode(expectedProduct.getClickUrl().toString(), CHARSET.name());
    String expectedPrivacyUrl = URLEncoder
        .encode(expectedAssets.getPrivacyOptOutClickUrl().toString(), CHARSET.name());

    String html = loadDfpHtmlNative(validNativeAdUnit);

    // TODO In native template, in DFP configuration, there is nothing that use advertiser data.
    //  This prevent automated testing on this part. We may extends the actual template to use them.
    //  However, we should verify that it's a dedicated DFP ad unit for our tests. Else we may have
    //  a side effect.

    assertTrue(html.contains(expectedProduct.getTitle()));
    assertTrue(html.contains(expectedProduct.getDescription()));
    assertTrue(html.contains(expectedProduct.getImageUrl().toString()));
    assertTrue(html.contains(expectedProduct.getPrice()));
    assertTrue(html.contains(expectedClickUrl));
    assertTrue(html.contains(expectedProduct.getCallToAction()));
    assertTrue(html.contains(expectedAssets.getPrivacyOptOutImageUrl().toString()));
    assertTrue(html.contains(expectedPrivacyUrl));
    assertTrue(html.contains(expectedAssets.getPrivacyLongLegalText()));
  }

  @Test
  public void loadingDfpBanner_GivenValidNative_HtmlInDfpViewShouldTriggerImpressionPixels()
      throws Exception {
    List<String> expectedPixels = new ArrayList<>();
    for (URL pixel : STUB_NATIVE_ASSETS.getImpressionPixels()) {
      expectedPixels.add(pixel.toString());
    }

    String html = loadDfpHtmlNative(validNativeAdUnit);

    List<String> firedUrls = webViewLookup.lookForLoadedResources(html, CHARSET).get();
    expectedPixels.removeAll(firedUrls);

    assertTrue(expectedPixels.isEmpty());
  }

  @Test
  public void loadingDfpBanner_GivenDemoBanner_DfpViewContainsDisplayUrl() throws Exception {
    givenUsingCdbProd();
    ResultCaptor<CdbResponse> cdbResultCaptor = mockedDependenciesRule.captorCdbResult();

    String html = loadDfpHtmlBanner(demoBannerAdUnit);

    assertDfpViewContainsDisplayUrl(cdbResultCaptor, html);
  }

  @Test
  public void loadingDfpBanner_GivenDemoInterstitial_DfpViewContainsDisplayUrl() throws Exception {
    givenUsingCdbProd();
    ResultCaptor<CdbResponse> cdbResultCaptor = mockedDependenciesRule.captorCdbResult();

    String html = loadDfpHtmlInterstitial(demoInterstitialAdUnit);

    assertDfpViewContainsDisplayUrl(cdbResultCaptor, html);
  }

  private void assertDfpViewContainsDisplayUrl(
      ResultCaptor<CdbResponse> cdbResultCaptor,
      String html
  ) {
    // The DFP webview replace the & by &amp; in attribute values.
    // So we need to replace them back in order to compare its content to our display URL.
    // This is valid HTML. See https://www.w3.org/TR/xhtml1/guidelines.html#C_12
    html = html.replace("&amp;", "&");

    String displayUrl = cdbResultCaptor.getLastCaptureValue().getSlots().get(0).getDisplayUrl();
    assertTrue(html.contains(displayUrl));
  }

  @Test
  public void loadingDfpBanner_GivenInvalidBanner_DfpViewDoesNotContainCreative() throws Exception {
    String html = loadDfpHtmlBanner(invalidBannerAdUnit);

    assertFalse(html.contains(STUB_CREATIVE_IMAGE));
  }

  @Test
  public void loadingDfpBanner_GivenInvalidInterstitial_DfpViewDoesNotContainCreative()
      throws Exception {
    String html = loadDfpHtmlInterstitial(invalidInterstitialAdUnit);

    assertFalse(html.contains(STUB_CREATIVE_IMAGE));
  }

  @Test
  public void loadingDfpBanner_GivenInvalidNative_DfpViewDoesNotContainProductImage()
      throws Exception {
    String html = loadDfpHtmlNative(invalidNativeAdUnit);

    assertFalse(html.contains(STUB_CREATIVE_IMAGE));
  }

  private String loadDfpHtmlBanner(BannerAdUnit adUnit) throws Exception {
    return loadDfpHtmlBannerOrNative(adUnit, createDfpBannerView());
  }

  private String loadDfpHtmlNative(NativeAdUnit adUnit) throws Exception {
    return loadDfpHtmlBannerOrNative(adUnit, createDfpNativeView());
  }

  private String loadDfpHtmlBannerOrNative(AdUnit adUnit, AdManagerAdView adManagerAdView)
      throws Exception {
    givenInitializedCriteo(adUnit);

    Builder builder = new Builder();

    loadBidAndWait(adUnit, builder);

    AdManagerAdRequest request = builder.build();

    DfpSync dfpSync = new DfpSync(adManagerAdView);

    runOnMainThreadAndWait(() -> adManagerAdView.loadAd(request));
    dfpSync.waitForBid();

    return webViewLookup.lookForHtmlContent(adManagerAdView).get();
  }

  private String loadDfpHtmlInterstitial(InterstitialAdUnit adUnit) throws Exception {
    givenInitializedCriteo(adUnit);

    Builder builder = new Builder();

    loadBidAndWait(adUnit, builder);

    AdManagerAdRequest request = builder.build();

    DfpSync dfpSync = new DfpSync();
    SyncInterstitialAdListener syncInterstitialAdListener = dfpSync.getSyncInterstitialAdListener();

    runOnMainThreadAndWait(() -> {
      AdManagerInterstitialAd.load(
          context,
          DFP_INTERSTITIAL_ID,
          request,
          syncInterstitialAdListener
      );
    });
    dfpSync.waitForBid();

    View interstitialView = getRootView(webViewLookup.lookForResumedActivity(() -> {
      runOnMainThreadAndWait(() -> {
        AdManagerInterstitialAd adManagerInterstitialAd = syncInterstitialAdListener.lastInterstitialAd;
        adManagerInterstitialAd.show(activityRule.getActivity());
      });
    }).get(DFP_INTERSTITIAL_OPENING_TIMEOUT_MS, TimeUnit.MILLISECONDS));

    return webViewLookup.lookForHtmlContent(interstitialView).get();
  }

  private AdManagerAdView createDfpBannerView() {
    AdManagerAdView adManagerAdView = new AdManagerAdView(context);
    adManagerAdView.setAdSizes(com.google.android.gms.ads.AdSize.BANNER);
    adManagerAdView.setAdUnitId(DFP_BANNER_ID);
    return adManagerAdView;
  }

  private AdManagerAdView createDfpNativeView() {
    AdManagerAdView adManagerAdView = new AdManagerAdView(context);
    adManagerAdView.setAdSizes(com.google.android.gms.ads.AdSize.FLUID);
    adManagerAdView.setAdUnitId(DFP_NATIVE_ID);
    return adManagerAdView;
  }

  private void assertCriteoMacroAreInjectedInDfpBuilder(Builder builder, boolean isVideo) {
    Bundle customTargeting = builder.build().getCustomTargeting();

    assertNotNull(customTargeting.getString(MACRO_CPM));
    assertNotNull(customTargeting.getString(MACRO_DISPLAY_URL));
    assertNotNull(customTargeting.getString(MACRO_SIZE));
    if (isVideo) {
      assertNotNull(customTargeting.getString(MACRO_FORMAT));
      assertEquals(4, customTargeting.size());
    } else {
      assertEquals(3, customTargeting.size());
    }
  }

  private void assertBidRequestHasGoodProfileId() throws Exception {
    // AppBidding declaration is delayed when bid is consumed. So the declaration is only visible from the next bid.
    loadBidAndWait(invalidBannerAdUnit, new AdManagerAdRequest.Builder());

    verify(api, atLeastOnce()).loadCdb(
        argThat(request -> request.getProfileId() == Integration.GAM_APP_BIDDING.getProfileId()),
        any()
    );
  }

  private void loadBidAndWait(AdUnit adUnit, Builder builder) {
    Criteo.getInstance().loadBid(
        adUnit,
        new ContextData(),
        bid -> Criteo.getInstance().enrichAdObjectWithBid(builder, bid)
    );
    waitForBids();
  }

  private void waitForBids() {
    mockedDependenciesRule.waitForIdleState();
  }

  private void givenUsingCdbProd() {
    when(buildConfigWrapper.getCdbUrl()).thenReturn(PROD_CDB_URL);
    when(mockedDependenciesRule.getDependencyProvider().provideCriteoPublisherId()).thenReturn(
        PROD_CP_ID);
  }

  private void givenInitializedCriteo(@NonNull AdUnit... adUnits) throws CriteoInitException {
    if (config.isLiveBiddingEnabled()) {
      // Empty it to show that prefetch has no influence
      adUnits = new AdUnit[]{};
    }

    CriteoUtil.givenInitializedCriteo(adUnits);
    waitForBids();
  }

  private void givenLiveBiddingEnabled() {
    when(config.isLiveBiddingEnabled()).thenReturn(true);
  }

  static final class DfpSync {

    private final CountDownLatch isLoaded = new CountDownLatch(1);

    private DfpSync(AdManagerAdView adView) {
      adView.setAdListener(new SyncAdListener());
    }

    private DfpSync() {
    }

    SyncInterstitialAdListener getSyncInterstitialAdListener() {
      return new SyncInterstitialAdListener();
    }

    void waitForBid() throws InterruptedException {
      isLoaded.await();
    }

    private class SyncAdListener extends AdListener {

      @Override
      public void onAdLoaded() {
        isLoaded.countDown();
      }

      @Override
      public void onAdFailedToLoad(LoadAdError loadAdError) {
        LoggerFactory.getLogger(DfpHeaderBiddingFunctionalTest.class)
            .debug("onAdFailedToLoad for reason %d.", loadAdError);
        isLoaded.countDown();
      }
    }

    class SyncInterstitialAdListener extends AdManagerInterstitialAdLoadCallback {
      private AdManagerInterstitialAd lastInterstitialAd;

      @Override
      public void onAdLoaded(@NonNull AdManagerInterstitialAd adManagerInterstitialAd) {
        lastInterstitialAd = adManagerInterstitialAd;
        isLoaded.countDown();
      }

      @Override
      public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
        LoggerFactory.getLogger(DfpHeaderBiddingFunctionalTest.class)
            .debug("onAdFailedToLoad for reason %d. "
                    + "See: https://developers.google.com/android/reference/com/google/android/gms/ads/admanager/AdManagerAdRequest",
                loadAdError);
        isLoaded.countDown();
      }
    }
  }
}
