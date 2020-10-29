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

package com.criteo.publisher.context

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.TYPE_BLUETOOTH
import android.net.ConnectivityManager.TYPE_ETHERNET
import android.net.ConnectivityManager.TYPE_MOBILE
import android.net.ConnectivityManager.TYPE_VPN
import android.net.ConnectivityManager.TYPE_WIFI
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.telephony.TelephonyManager
import android.telephony.TelephonyManager.NETWORK_TYPE_NR
import android.telephony.TelephonyManager.NETWORK_TYPE_UNKNOWN
import com.criteo.publisher.context.ConnectionTypeFetcher.ConnectionType
import com.criteo.publisher.context.ConnectionTypeFetcher.ConnectionType.CELLULAR_2G
import com.criteo.publisher.context.ConnectionTypeFetcher.ConnectionType.CELLULAR_3G
import com.criteo.publisher.context.ConnectionTypeFetcher.ConnectionType.CELLULAR_4G
import com.criteo.publisher.context.ConnectionTypeFetcher.ConnectionType.CELLULAR_5G
import com.criteo.publisher.context.ConnectionTypeFetcher.ConnectionType.CELLULAR_UNKNOWN
import com.criteo.publisher.context.ConnectionTypeFetcher.ConnectionType.WIFI
import com.criteo.publisher.context.ConnectionTypeFetcher.ConnectionType.WIRED
import com.criteo.publisher.mock.MockedDependenciesRule
import com.criteo.publisher.mock.SpyBean
import com.nhaarman.mockitokotlin2.KStubbing
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.Rule
import org.junit.Test

@Suppress("DEPRECATION")
class ConnectionTypeFetcherTest {

  @Rule
  @JvmField
  val mockedDependenciesRule = MockedDependenciesRule()

  @SpyBean
  private lateinit var context: Context

  @SpyBean
  private lateinit var connectionTypeFetcher: ConnectionTypeFetcher

  @Test
  fun fetchConnectionType_GivenNoMock_DoesNotThrow() {
    assertThatCode {
      connectionTypeFetcher.fetchConnectionType()
    }.doesNotThrowAnyException()
  }

  @Test
  fun fetchConnectionType_GivenNoConnectivityService_ReturnNull() {
    doReturn(null).whenever(context).getSystemService(any())

    val connectionType = connectionTypeFetcher.fetchConnectionType()

    assertThat(connectionType).isNull()
  }

  @Test
  fun fetchConnectionType_DeprecatedWayWorking_NoActiveNetwork_ReturnNull() {
    givenMockedConnectivityService {
      on { activeNetworkInfo } doReturn null
    }

    val connectionType = connectionTypeFetcher.fetchConnectionType()

    assertThat(connectionType).isNull()
  }

  @Test
  fun fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected() {
    fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(TYPE_ETHERNET, expected = WIRED)
    fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(TYPE_WIFI, expected = WIFI)
    fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(TYPE_BLUETOOTH, expected = null)
    fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(TYPE_VPN, expected = null)
    fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(-1 /* TYPE_NONE */, expected = null)
    fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(
        TYPE_MOBILE,
        NETWORK_TYPE_UNKNOWN,
        expected = CELLULAR_UNKNOWN
    )

    cellular2GTypes.forEach {
      fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(TYPE_MOBILE, it, expected = CELLULAR_2G)
    }

    cellular3GTypes.forEach {
      fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(TYPE_MOBILE, it, expected = CELLULAR_3G)
    }

    cellular4GTypes.forEach {
      fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(TYPE_MOBILE, it, expected = CELLULAR_4G)
    }

    fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(
        TYPE_MOBILE,
        NETWORK_TYPE_NR,
        expected = CELLULAR_5G
    )
  }

  private fun fetchConnectionType_DeprecatedWayWorking_ActiveNetwork_ReturnExpected(
      type: Int,
      subType: Int = -1,
      expected: ConnectionType?
  ) {
    val networkInfo = mock<NetworkInfo>() {
      on { this.type } doReturn type
      on { this.subtype } doReturn subType
    }

    givenMockedConnectivityService {
      on { activeNetworkInfo } doReturn networkInfo
    }

    val connectionType = connectionTypeFetcher.fetchConnectionType()

    assertThat(connectionType).isEqualTo(expected)
  }

  @Test
  fun fetchConnectionType_DeprecatedWayNotWorking_NoNetworkCapabilities_ReturnNull() {
    val network = mock<Network>()

    givenMockedConnectivityService {
      on { activeNetwork } doReturn network
      on { getNetworkCapabilities(network) } doReturn null
    }

    givenDeprecatedWayNotWorking()

    val connectionType = connectionTypeFetcher.fetchConnectionType()

    assertThat(connectionType).isNull()
  }

  @Test
  fun fetchConnectionType_DeprecatedWayNotWorking_NoExpectedCapabilities_ReturnNull() {
    val network = mock<Network>()
    val networkCapabilities = NetworkCapabilities(null)

    givenMockedConnectivityService {
      on { activeNetwork } doReturn network
      on { getNetworkCapabilities(network) } doReturn networkCapabilities
    }

    givenDeprecatedWayNotWorking()

    val connectionType = connectionTypeFetcher.fetchConnectionType()

    assertThat(connectionType).isNull()
  }

  @Test
  fun fetchConnectionType_DeprecatedWayNotWorking_EthernetCapabilities_ReturnWired() {
    val network = mock<Network>()
    val networkCapabilities = NetworkCapabilities(null)
    doReturn(true).whenever(connectionTypeFetcher).isWired(networkCapabilities) // wired is prio against wifi/cellular
    doReturn(true).whenever(connectionTypeFetcher).isWifi(networkCapabilities)
    doReturn(true).whenever(connectionTypeFetcher).isCellular(networkCapabilities)

    givenMockedConnectivityService {
      on { activeNetwork } doReturn network
      on { getNetworkCapabilities(network) } doReturn networkCapabilities
    }

    givenDeprecatedWayNotWorking()

    val connectionType = connectionTypeFetcher.fetchConnectionType()

    assertThat(connectionType).isEqualTo(WIRED)
  }

  @Test
  fun fetchConnectionType_DeprecatedWayNotWorking_WifiCapabilities_ReturnWifi() {
    val network = mock<Network>()
    val networkCapabilities = NetworkCapabilities(null)
    doReturn(true).whenever(connectionTypeFetcher).isWifi(networkCapabilities) // wifi is prio against cellular
    doReturn(true).whenever(connectionTypeFetcher).isCellular(networkCapabilities)

    givenMockedConnectivityService {
      on { activeNetwork } doReturn network
      on { getNetworkCapabilities(network) } doReturn networkCapabilities
    }

    givenDeprecatedWayNotWorking()

    val connectionType = connectionTypeFetcher.fetchConnectionType()

    assertThat(connectionType).isEqualTo(WIFI)
  }

  @Test
  fun fetchConnectionType_DeprecatedWayNotWorking_CellularCapabilities_ReturnExpectedUnknownCellular() {
    val network = mock<Network>()
    val networkCapabilities = NetworkCapabilities(null)
    doReturn(true).whenever(connectionTypeFetcher).isCellular(networkCapabilities)

    givenMockedConnectivityService {
      on { activeNetwork } doReturn network
      on { getNetworkCapabilities(network) } doReturn networkCapabilities
    }

    givenDeprecatedWayNotWorking()

    val connectionType = connectionTypeFetcher.fetchConnectionType()

    assertThat(connectionType).isEqualTo(CELLULAR_UNKNOWN)
  }

  private fun givenMockedConnectivityService(
      stubbing: KStubbing<ConnectivityManager>.(ConnectivityManager) -> Unit = {}
  ): ConnectivityManager {
    val mockedService = mock(stubbing = stubbing)
    doReturn(mockedService).whenever(context).getSystemService(Context.CONNECTIVITY_SERVICE)
    return mockedService
  }

  private fun givenDeprecatedWayNotWorking() {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    manager.stub {
      on { activeNetworkInfo } doThrow NoSuchMethodError::class
    }
  }

  private companion object {
    val cellular2GTypes = listOf(
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN,
        TelephonyManager.NETWORK_TYPE_GSM
    )

    val cellular3GTypes = listOf(
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA
    )

    val cellular4GTypes = listOf(
        TelephonyManager.NETWORK_TYPE_LTE,
        TelephonyManager.NETWORK_TYPE_IWLAN,
        19
    )
  }
}