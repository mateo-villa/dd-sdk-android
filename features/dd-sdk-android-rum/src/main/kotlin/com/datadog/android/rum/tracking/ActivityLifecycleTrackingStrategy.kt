/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.annotation.MainThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.internal.RumFeature

/**
 * The ActivityLifecycleTrackingStrategy as an [Application.ActivityLifecycleCallbacks]
 * based implementation of the [TrackingStrategy].
 */
abstract class ActivityLifecycleTrackingStrategy :
    Application.ActivityLifecycleCallbacks,
    TrackingStrategy {

    /** The [FeatureSdkCore] this [TrackingStrategy] reports to. */
    protected lateinit var sdkCore: FeatureSdkCore

    internal val internalLogger: InternalLogger
        get() = if (this::sdkCore.isInitialized) {
            sdkCore.internalLogger
        } else {
            InternalLogger.UNBOUND
        }

    // region TrackingStrategy

    override fun register(sdkCore: SdkCore, context: Context) {
        if (context is Application) {
            this.sdkCore = sdkCore as FeatureSdkCore
            context.registerActivityLifecycleCallbacks(this)
        } else {
            (sdkCore as FeatureSdkCore).internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                {
                    "In order to use the RUM automatic tracking feature you will have" +
                        " to use the Application context when initializing the SDK"
                }
            )
        }
    }

    override fun unregister(context: Context?) {
        if (context is Application) {
            context.unregisterActivityLifecycleCallbacks(this)
        }
    }

    // endregion

    // region Application.ActivityLifecycleCallbacks

    @MainThread
    override fun onActivityPaused(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivityStarted(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivityDestroyed(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No Op
    }

    @MainThread
    override fun onActivityStopped(activity: Activity) {
        // No Op
    }

    @MainThread
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (::sdkCore.isInitialized) {
            GlobalRumMonitor
                .get(sdkCore)
                ._getInternal()
                ?.setSyntheticsAttributeFromIntent(activity.intent)
        }
    }

    @MainThread
    override fun onActivityResumed(activity: Activity) {
        // No Op
    }

    // endregion

    // region Helper

    /**
     * Runs a block if this tracking strategy is bound with an [SdkCore] instance.
     * @param T the return type for the block
     * @param block the block to run accepting the current SDK instance
     * @return the result of the block, or null if no SDK instance is available yet
     */
    protected fun <T> withSdkCore(block: (FeatureSdkCore) -> T): T? {
        return if (this::sdkCore.isInitialized) {
            block(sdkCore)
        } else {
            InternalLogger.UNBOUND.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                {
                    RumFeature.RUM_FEATURE_NOT_YET_INITIALIZED +
                        " Cannot provide SDK instance for view tracking."
                }
            )
            null
        }
    }

    // endregion

    internal companion object {

        internal const val EXTRA_SYNTHETICS_TEST_ID = "_dd.synthetics.test_id"
        internal const val EXTRA_SYNTHETICS_RESULT_ID = "_dd.synthetics.result_id"
    }
}
