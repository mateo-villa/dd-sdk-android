/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.rules.SessionReplayTestRule
import com.datadog.tools.unit.ConditionWatcher
import org.junit.Rule
import org.junit.Test

internal class ConsentGrantedSrTest : SrSnapshotTest<SessionReplayPlaygroundActivity>() {

    @get:Rule
    val rule = SessionReplayTestRule(
        SessionReplayPlaygroundActivity::class.java,
        trackingConsent = TrackingConsent.GRANTED,
        keepRequests = true
    )

    @Test
    fun verifySessionFirstSnapshot() {
        // Wait to make sure all batches are consumed
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        runInstrumentationScenario(rule)
        ConditionWatcher {
            // verify the captured log events into the MockedWebServer
            verifyExpectedSrData(
                rule.getRequests(RuntimeConfig.sessionReplayEndpointUrl),
                EXPECTED_PAYLOAD_FILE_NAME
            )
            true
        }.doWait(timeoutMs = INITIAL_WAIT_MS)
    }

    companion object {
        const val EXPECTED_PAYLOAD_FILE_NAME = "consent_granted_sr_test_payload.json"
    }
}