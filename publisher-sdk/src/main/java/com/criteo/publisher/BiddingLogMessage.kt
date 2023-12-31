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

package com.criteo.publisher

import android.util.Log
import com.criteo.publisher.integration.Integration
import com.criteo.publisher.logging.LogMessage
import com.criteo.publisher.model.AdUnit
import com.criteo.publisher.model.CacheAdUnit

internal object BiddingLogMessage {

  @JvmStatic
  fun onConsumableBidLoaded(adUnit: AdUnit, bid: Bid?) = LogMessage(message =
    "Getting bid response for $adUnit. Bid: ${bid?.loggingId}, price: ${bid?.price}"
  )

  @JvmStatic
  fun onGlobalSilentModeEnabled(seconds: Int) = LogMessage(message =
    "Silent mode is enabled, no requests will be fired for the next $seconds seconds"
  )

  @JvmStatic
  fun onInvalidAdUnit(adUnit: CacheAdUnit) = LogMessage(
      level = Log.WARN,
      message = "Found an invalid AdUnit: $adUnit",
      logId = "onInvalidAdUnit"
  )

  @JvmStatic
  fun onUnsupportedAdFormat(cacheAdUnit: CacheAdUnit, integration: Integration) = LogMessage(
      level = Log.ERROR,
      message = "$cacheAdUnit requested but it is not supported for $integration",
      logId = "onUnsupportedAdFormat"
  )
}
