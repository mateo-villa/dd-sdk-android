/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.event.EventMapper
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.SdkCore

/**
 * This class exposes internal methods that are used by other Datadog modules and cross platform
 * frameworks. It is not meant for public use.
 *
 * DO NOT USE this class or its methods if you are not working on the internals of the Datadog SDK
 * or one of the cross platform frameworks.
 *
 * Methods, members, and functionality of this class  are subject to change without notice, as they
 * are not considered part of the public interface of the Datadog SDK.
 */
@Suppress(
    "UndocumentedPublicClass",
    "UndocumentedPublicFunction",
    "UndocumentedPublicProperty",
    "ClassName",
    "ClassNaming",
    "VariableNaming"
)
class _InternalProxy internal constructor(
    sdkCore: SdkCore,
    // TODO RUMM-0000 Shouldn't be nullable
    private val coreFeature: CoreFeature?
) {
    @Suppress("StringLiteralDuplication")
    class _TelemetryProxy internal constructor(private val sdkCore: SdkCore) {

        private val rumFeature: FeatureScope?
            get() {
                return sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            }

        fun debug(message: String) {
            rumFeature?.sendEvent(mapOf("type" to "telemetry_debug", "message" to message))
        }

        fun error(message: String, throwable: Throwable? = null) {
            rumFeature?.sendEvent(
                mapOf(
                    "type" to "telemetry_error",
                    "message" to message,
                    "throwable" to throwable
                )
            )
        }

        fun error(message: String, stack: String?, kind: String?) {
            rumFeature?.sendEvent(
                mapOf(
                    "type" to "telemetry_error",
                    "message" to message,
                    "stacktrace" to stack,
                    "kind" to kind
                )
            )
        }
    }

    @Suppress("PropertyName")
    val _telemetry: _TelemetryProxy = _TelemetryProxy(sdkCore)

    fun setCustomAppVersion(version: String) {
        coreFeature?.packageVersionProvider?.version = version
    }

    companion object {
        @Suppress("FunctionMaxLength")
        fun setTelemetryConfigurationEventMapper(
            builder: RumFeature.Builder,
            eventMapper: EventMapper<TelemetryConfigurationEvent>
        ): RumFeature.Builder {
            return builder.setTelemetryConfigurationEventMapper(eventMapper)
        }
    }
}
