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

package com.criteo.publisher.util

import android.content.Context
import android.util.DisplayMetrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

class AndroidUtilUnitTest {

  @Rule
  @JvmField
  val mockitoRule = MockitoJUnit.rule()

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var context: Context

  @InjectMocks
  private lateinit var androidUtil: AndroidUtil

  @Test
  fun dpToPixel_GivenIntegerDensity_ReturnsScaledValue() {
    givenDensity(2.0f)

    assertThat(androidUtil.dpToPixel(1)).isEqualTo(2)
    assertThat(androidUtil.dpToPixel(42)).isEqualTo(84)
  }

  @Test
  fun dpToPixel_GivenFloatDensity_ReturnsScaledCeilValue() {
    givenDensity(31.82f)

    assertThat(androidUtil.dpToPixel(1)).isEqualTo(32)
    assertThat(androidUtil.dpToPixel(42)).isEqualTo(1337)
  }

  private fun givenDensity(density: Float) {
    val metrics = DisplayMetrics()
    metrics.density = density
    context.resources.stub {
      on { displayMetrics } doReturn metrics
    }
  }
}
