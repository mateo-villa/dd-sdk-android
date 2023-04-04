/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.nightly.rules.NightlyTestRule
import com.datadog.android.nightly.utils.initializeSdk
import com.datadog.android.nightly.utils.invokeMethod
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.v2.api.SdkCore
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class RumMonitorBuilderE2ETests {

    @get:Rule
    val forge = ForgeRule()

    @get:Rule
    val nightlyTestRule = NightlyTestRule()

    lateinit var sdkCore: SdkCore

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor$Builder#constructor(com.datadog.android.v2.api.SdkCore)
     * apiMethodSignature: com.datadog.android.rum.RumMonitor$Builder#fun sampleRumSessions(Float): Builder
     * apiMethodSignature: com.datadog.android.rum.RumMonitor$Builder#fun build(): RumMonitor
     * apiMethodSignature: com.datadog.android.rum.GlobalRum#fun registerIfAbsent(java.util.concurrent.Callable<RumMonitor>): Boolean
     */
    @Test
    fun rum_rummonitor_builder_sample_all_in() {
        val testMethodName = "rum_rummonitor_builder_sample_all_in"

        sdkCore = initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            forgeSeed = forge.seed,
            rumMonitorProvider = { sdkCore ->
                measureRumMonitorInitialize {
                    RumMonitor.Builder(sdkCore).build()
                }
            }
        )

        sendAllRumEvents(forge, sdkCore, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor$Builder#constructor(com.datadog.android.v2.api.SdkCore)
     * apiMethodSignature: com.datadog.android.rum.RumMonitor$Builder#fun sampleRumSessions(Float): Builder
     * apiMethodSignature: com.datadog.android.rum.RumMonitor$Builder#fun build(): RumMonitor
     */
    @Test
    fun rum_rummonitor_builder_sample_all_out() {
        val testMethodName = "rum_rummonitor_builder_sample_all_out"
        initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            forgeSeed = forge.seed,
            rumMonitorProvider = { sdkCore ->
                measureRumMonitorInitialize {
                    RumMonitor.Builder(sdkCore).sampleRumSessions(0f).build()
                }
            }
        )
        sendAllRumEvents(forge, sdkCore, testMethodName)
    }

    /**
     * apiMethodSignature: com.datadog.android.rum.RumMonitor$Builder#constructor(com.datadog.android.v2.api.SdkCore)
     * apiMethodSignature: com.datadog.android.rum.RumMonitor$Builder#fun sampleRumSessions(Float): Builder
     * apiMethodSignature: com.datadog.android.rum.RumMonitor$Builder#fun build(): RumMonitor
     */
    @Test
    fun rum_rummonitor_builder_sample_in_75_percent() {
        val testMethodName = "rum_rummonitor_builder_sample_in_75_percent"
        val eventsNumber = 100
        initializeSdk(
            InstrumentationRegistry.getInstrumentation().targetContext,
            forgeSeed = forge.seed,
            rumMonitorProvider = { sdkCore ->
                measureRumMonitorInitialize {
                    RumMonitor.Builder(sdkCore).sampleRumSessions(75f).build()
                }
            }
        )
        repeat(eventsNumber) {
            // we do not want to add the test method name in the parent View name as
            // this will add extra events to the monitor query value
            sendRandomRumEvent(forge, sdkCore, testMethodName, parentViewEventName = "")
            // expire the session here
            GlobalRum.get(sdkCore).invokeMethod("resetSession")
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }
}
