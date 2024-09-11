/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.tests.elmyr

import com.datadog.android.internal.telemetry.TelemetryEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class TelemetryApiUsageEventForgeryFactory : ForgeryFactory<TelemetryEvent.ApiUsage> {

    override fun getForgery(forge: Forge): TelemetryEvent.ApiUsage {
        return TelemetryEvent.ApiUsage.AddViewLoadingTime(
            overwrite = forge.aBool(),
            noView = forge.aBool(),
            noActiveView = forge.aBool(),
            additionalProperties = forge.aMap { forge.aString() to forge.aString() }.toMutableMap()
        )
    }
}
