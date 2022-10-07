/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext.Companion.NULL_UUID
import com.datadog.android.sessionreplay.utils.RumContextProvider
import com.datadog.android.sessionreplay.utils.SessionReplayRumContext

internal class SessionReplayRumContextProvider : RumContextProvider {
    override fun getRumContext(): SessionReplayRumContext {
        return GlobalRum.getRumContext().let {
            SessionReplayRumContext(
                applicationId = it.applicationId,
                sessionId = it.sessionId,
                viewId = it.viewId
                    ?: NULL_UUID
            )
        }
    }
}
