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

package com.criteo.publisher.csm;

import static com.criteo.publisher.CriteoUtil.givenInitializedCriteo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.test.filters.FlakyTest;
import com.criteo.publisher.Clock;
import com.criteo.publisher.Criteo;
import com.criteo.publisher.CriteoInitException;
import com.criteo.publisher.TestAdUnits;
import com.criteo.publisher.context.ContextData;
import com.criteo.publisher.csm.MetricRequest.MetricRequestFeedback;
import com.criteo.publisher.integration.Integration;
import com.criteo.publisher.integration.IntegrationRegistry;
import com.criteo.publisher.mock.MockedDependenciesRule;
import com.criteo.publisher.mock.SpyBean;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.network.CdbMock;
import com.criteo.publisher.network.PubSdkApi;
import com.criteo.publisher.util.BuildConfigWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@FlakyTest(detail = "CSM are triggered concurrently of bid")
public class CsmFunctionalTest {

  @Rule
  public MockedDependenciesRule mockedDependenciesRule = new MockedDependenciesRule();

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Inject
  private IntegrationRegistry integrationRegistry;

  @SpyBean
  private BuildConfigWrapper buildConfigWrapper;

  @SpyBean
  private PubSdkApi api;

  @SpyBean
  private Clock clock;

  @Inject
  private CdbMock cdbMock;

  @Before
  public void setUp() throws Exception {
    integrationRegistry.declare(Integration.IN_HOUSE);
    givenConsentGiven();
  }

  @Test
  public void givenPrefetchAdUnitsWithBidsThenConsumption_CallApiWithCsmOfConsumedBid() throws Exception {
    givenInitializedCriteo(TestAdUnits.BANNER_320_50, TestAdUnits.INTERSTITIAL);
    waitForIdleState();

    loadBid(TestAdUnits.BANNER_320_50);
    waitForIdleState();

    AtomicReference<String> firstImpressionId = new AtomicReference<>();
    AtomicReference<String> firstRequestGroupId = new AtomicReference<>();

    // A third call is needed to trigger the sending of metrics to CSM. This is because,
    // the execution of onBidConsumed() callback happens after the second call to CDB.
    // An INTERSTITIAL AdUnit is set here on purpose to verify that the metric that is sent
    // to CSM relates to BANNER_320_50, which is the one that was consumed and whose metric
    // was pushed to the sending queue.
    loadBid(TestAdUnits.INTERSTITIAL);
    waitForIdleState();

    verify(api).postCsm(argThat(request -> {
      assertRequestHeaderIsExpected(request);

      // There is only one expected because the second one was not ready when sending was triggered.
      assertEquals(1, request.getFeedbacks().size());
      MetricRequestFeedback feedback = request.getFeedbacks().get(0);
      assertItRepresentsConsumedBid(feedback);
      firstImpressionId.set(feedback.getSlots().get(0).getImpressionId());
      firstRequestGroupId.set(feedback.getRequestGroupId());

      return true;
    }));

    clearInvocations(api);
    loadBid(TestAdUnits.BANNER_320_50);
    waitForIdleState();

    verify(api).postCsm(argThat(request -> {
      assertRequestHeaderIsExpected(request);

      assertEquals(1, request.getFeedbacks().size());
      MetricRequestFeedback feedback = request.getFeedbacks().get(0);
      assertItRepresentsConsumedBid(feedback);
      assertNotEquals(firstImpressionId.get(), feedback.getSlots().get(0).getImpressionId());

      // This two metrics come from the same CDB request (prefetch) and should have the same ID
      assertEquals(firstRequestGroupId.get(), feedback.getRequestGroupId());

      return true;
    }));
  }

  /**
   * Consent status is updated after the first response from CDB. Therefore, a first bid-request
   * is made here to get the wanted side-effect.
   */
  private void givenConsentGiven() throws CriteoInitException {
    givenInitializedCriteo(TestAdUnits.BANNER_320_480);
    waitForIdleState();
    MetricHelper.cleanState(mockedDependenciesRule.getDependencyProvider());
  }

  @Test
  public void givenConsumedExpiredBid_CallApiWithCsmOfConsumedExpiredBid() throws Exception {
    when(clock.getCurrentTimeInMillis()).thenReturn(0L);
    givenInitializedCriteo(TestAdUnits.BANNER_320_50, TestAdUnits.INTERSTITIAL);
    waitForIdleState();

    when(clock.getCurrentTimeInMillis()).thenReturn(Long.MAX_VALUE);
    loadBid(TestAdUnits.INTERSTITIAL);
    waitForIdleState();

    // A third call is needed to trigger the sending of metrics to CSM. This is because,
    // the execution of onBidConsumed() callback happens after the second call to CDB.
    // An BANNER_320_50 AdUnit is set here on purpose to verify that the metric that is sent
    // to CSM relates to INTERSTITIAL, which is the one that was consumed and whose metric
    // was pushed to the sending queue.
    loadBid(TestAdUnits.BANNER_320_50);
    waitForIdleState();

    verify(api).postCsm(argThat(request -> {
      assertRequestHeaderIsExpected(request);

      assertEquals(1, request.getFeedbacks().size());
      MetricRequestFeedback feedback = request.getFeedbacks().get(0);
      assertItRepresentsExpiredConsumedBid(feedback);

      return true;
    }));
  }

  @Test
  public void givenNoBidFromCdb_CallApiWithCsmOfNoBid() throws Exception {
    givenInitializedCriteo(TestAdUnits.BANNER_320_50, TestAdUnits.INTERSTITIAL_UNKNOWN);
    waitForIdleState();

    loadBid(TestAdUnits.INTERSTITIAL_UNKNOWN);
    waitForIdleState();

    verify(api).postCsm(argThat(request -> {
      assertRequestHeaderIsExpected(request);

      assertEquals(1, request.getFeedbacks().size());
      MetricRequestFeedback feedback = request.getFeedbacks().get(0);
      assertItRepresentsNoBid(feedback);

      return true;
    }));
  }

  @Test
  public void givenNetworkErrorFromCdb_CallApiWithCsmOfNetworkError() throws Exception {
    doThrow(IOException.class).when(api).loadCdb(any(), any());

    givenInitializedCriteo(TestAdUnits.BANNER_320_50, TestAdUnits.INTERSTITIAL);
    waitForIdleState();

    loadBid(TestAdUnits.INTERSTITIAL_UNKNOWN);
    waitForIdleState();

    verify(api).postCsm(argThat(request -> {
      assertRequestHeaderIsExpected(request);

      assertEquals(2, request.getFeedbacks().size());
      MetricRequestFeedback feedback1 = request.getFeedbacks().get(0);
      MetricRequestFeedback feedback2 = request.getFeedbacks().get(1);
      assertItRepresentsNetworkError(feedback1);
      assertItRepresentsNetworkError(feedback2);

      assertNotEquals(
          feedback1.getSlots().get(0).getImpressionId(),
          feedback2.getSlots().get(0).getImpressionId()
      );

      assertEquals(feedback1.getRequestGroupId(), feedback2.getRequestGroupId());

      return true;
    }));
  }

  @Test
  public void givenTimeoutErrorFromCdb_CallApiWithCsmOfTimeoutError() throws Exception {
    when(buildConfigWrapper.getNetworkTimeoutInMillis()).thenReturn(1);

    givenInitializedCriteo(TestAdUnits.BANNER_320_50, TestAdUnits.INTERSTITIAL);
    waitForIdleState();

    when(buildConfigWrapper.getNetworkTimeoutInMillis()).thenCallRealMethod();

    loadBid(TestAdUnits.INTERSTITIAL_UNKNOWN);
    waitForIdleState();

    verify(api).postCsm(argThat(request -> {
      assertRequestHeaderIsExpected(request);

      assertEquals(2, request.getFeedbacks().size());
      MetricRequestFeedback feedback1 = request.getFeedbacks().get(0);
      MetricRequestFeedback feedback2 = request.getFeedbacks().get(1);
      assertItRepresentsTimeoutError(feedback1);
      assertItRepresentsTimeoutError(feedback2);

      assertNotEquals(
          feedback1.getSlots().get(0).getImpressionId(),
          feedback2.getSlots().get(0).getImpressionId()
      );

      assertEquals(feedback1.getRequestGroupId(), feedback2.getRequestGroupId());

      return true;
    }));
  }

  @Test
  public void givenErrorWhenSendingCsm_QueueMetricsUntilCsmRequestWorksAgain() throws Exception {
    when(buildConfigWrapper.preconditionThrowsOnException()).thenReturn(false);
    doThrow(IOException.class).when(api).postCsm(any());

    when(clock.getCurrentTimeInMillis()).thenReturn(0L);
    givenInitializedCriteo(TestAdUnits.BANNER_320_50, TestAdUnits.INTERSTITIAL);
    waitForIdleState();

    // Consumed and not expired
    loadBid(TestAdUnits.INTERSTITIAL);
    waitForIdleState();

    // Consumed but expired
    // There is also a bid request here and consumed at timeout step
    when(clock.getCurrentTimeInMillis()).thenCallRealMethod();
    loadBid(TestAdUnits.INTERSTITIAL);
    waitForIdleState();

    // No bid
    loadBid(TestAdUnits.INTERSTITIAL_UNKNOWN);
    waitForIdleState();

    // Timeout
    cdbMock.simulatorSlowNetworkOnNextRequest();
    when(buildConfigWrapper.getNetworkTimeoutInMillis()).thenReturn(1);
    loadBid(TestAdUnits.INTERSTITIAL);
    waitForIdleState();
    when(buildConfigWrapper.getNetworkTimeoutInMillis()).thenCallRealMethod();

    // Network error
    doThrow(IOException.class).when(api).loadCdb(any(), any());
    loadBid(TestAdUnits.INTERSTITIAL);
    waitForIdleState();
    doCallRealMethod().when(api).loadCdb(any(), any());

    // CSM endpoint works again: on next bid request, there should metrics for all previous bids.
    clearInvocations(api);
    doCallRealMethod().when(api).postCsm(any());
    loadBid(TestAdUnits.BANNER_UNKNOWN);
    waitForIdleState();

    verify(api).postCsm(argThat(request -> {
      assertRequestHeaderIsExpected(request);

      List<MetricRequestFeedback> feedbacks = request.getFeedbacks();
      assertEquals(6, feedbacks.size());
      assertOnlyNSatisfy(feedbacks, 2, this::assertItRepresentsConsumedBid);
      assertOnlyNSatisfy(feedbacks, 1, this::assertItRepresentsExpiredConsumedBid);
      assertOnlyNSatisfy(feedbacks, 1, this::assertItRepresentsNoBid);
      assertOnlyNSatisfy(feedbacks, 1, this::assertItRepresentsTimeoutError);
      assertOnlyNSatisfy(feedbacks, 1, this::assertItRepresentsNetworkError);

      Set<String> impressionIds = new HashSet<>();
      Set<String> requestGroupIds = new HashSet<>();
      for (MetricRequestFeedback feedback : feedbacks) {
        impressionIds.add(feedback.getSlots().get(0).getImpressionId());
        requestGroupIds.add(feedback.getRequestGroupId());
      }

      assertEquals(6, impressionIds.size());
      assertEquals(6, requestGroupIds.size());

      return true;
    }));
  }

  private <T> void assertOnlyNSatisfy(
      Collection<T> elements,
      int expectedNumberOfValidatedElements,
      Consumer<T> validation) {
    List<T> validatedElements = new ArrayList<>();
    for (T element : elements) {
      try {
        validation.accept(element);
        validatedElements.add(element);
      } catch (AssertionError e) {
        // ignored
      }
    }

    assertThat(validatedElements).describedAs(
        "%d elements should match given validation among %s",
        expectedNumberOfValidatedElements,
        elements
    ).hasSize(expectedNumberOfValidatedElements);
  }

  private void assertRequestHeaderIsExpected(MetricRequest request) {
    assertEquals(integrationRegistry.getProfileId(), request.getProfileId());
    assertEquals(buildConfigWrapper.getSdkVersion(), request.getWrapperVersion());
  }

  private void assertItRepresentsConsumedBid(MetricRequestFeedback feedback) {
    assertEquals(0, feedback.getCdbCallStartElapsed());
    assertNotNull(feedback.getCdbCallEndElapsed());
    assertNotNull(feedback.getElapsed());
    assertFalse(feedback.isTimeout());
    assertNotNull(feedback.getRequestGroupId());
    assertEquals(1, feedback.getSlots().size());
    assertTrue(feedback.getSlots().get(0).getCachedBidUsed());
    assertNotNull(feedback.getSlots().get(0).getZoneId());
  }

  private void assertItRepresentsExpiredConsumedBid(MetricRequestFeedback feedback) {
    assertEquals(0, feedback.getCdbCallStartElapsed());
    assertNotNull(feedback.getCdbCallEndElapsed());
    assertNull(feedback.getElapsed());
    assertFalse(feedback.isTimeout());
    assertNotNull(feedback.getRequestGroupId());
    assertEquals(1, feedback.getSlots().size());
    assertTrue(feedback.getSlots().get(0).getCachedBidUsed());
    assertNotNull(feedback.getSlots().get(0).getZoneId());
  }

  private void assertItRepresentsNoBid(MetricRequestFeedback feedback) {
    assertEquals(0, feedback.getCdbCallStartElapsed());
    assertNotNull(feedback.getCdbCallEndElapsed());
    assertNull(feedback.getElapsed());
    assertFalse(feedback.isTimeout());
    assertNotNull(feedback.getRequestGroupId());
    assertEquals(1, feedback.getSlots().size());
    assertFalse(feedback.getSlots().get(0).getCachedBidUsed());
    assertNull(feedback.getSlots().get(0).getZoneId());
  }

  private void assertItRepresentsNetworkError(MetricRequestFeedback feedback) {
    assertEquals(0, feedback.getCdbCallStartElapsed());
    assertNull(feedback.getCdbCallEndElapsed());
    assertNull(feedback.getElapsed());
    assertFalse(feedback.isTimeout());
    assertNotNull(feedback.getRequestGroupId());
    assertEquals(1, feedback.getSlots().size());
    assertFalse(feedback.getSlots().get(0).getCachedBidUsed());
    assertNull(feedback.getSlots().get(0).getZoneId());
  }

  private void assertItRepresentsTimeoutError(MetricRequestFeedback feedback) {
    assertEquals(0, feedback.getCdbCallStartElapsed());
    assertNull(feedback.getCdbCallEndElapsed());
    assertNull(feedback.getElapsed());
    assertTrue(feedback.isTimeout());
    assertNotNull(feedback.getRequestGroupId());
    assertEquals(1, feedback.getSlots().size());
    assertFalse(feedback.getSlots().get(0).getCachedBidUsed());
    assertNull(feedback.getSlots().get(0).getZoneId());
  }

  private void waitForIdleState() {
    mockedDependenciesRule.waitForIdleState();
  }

  private void loadBid(@NonNull AdUnit adUnit) {
    Criteo.getInstance().loadBid(adUnit, new ContextData(), ignored -> { /* no op */ });
  }
}
