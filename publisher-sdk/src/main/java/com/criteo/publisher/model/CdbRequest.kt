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
package com.criteo.publisher.model

import com.criteo.publisher.annotation.OpenForTesting
import com.criteo.publisher.privacy.gdpr.GdprData
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@OpenForTesting
@JsonClass(generateAdapter = true)
data class CdbRequest(
    @Json(name = "id")
    val id: String,
    @Json(name = "publisher")
    val publisher: Publisher,
    @Json(name = "user")
    val user: User,
    @Json(name = "sdkVersion")
    val sdkVersion: String,
    @Json(name = "profileId")
    val profileId: Int,
    @Json(name = "gdprConsent")
    val gdprData: GdprData?,
    @Json(name = "slots")
    val slots: List<CdbRequestSlot>,
    @Json(name = "regs")
    val regs: CdbRegs?
)
