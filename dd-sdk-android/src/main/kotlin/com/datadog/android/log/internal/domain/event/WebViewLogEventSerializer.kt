/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain.event

import com.datadog.android.core.internal.constraints.DataConstraints
import com.datadog.android.core.internal.constraints.DatadogDataConstraints
import com.datadog.android.core.internal.persistence.Serializer
import com.datadog.android.log.model.WebViewLogEvent

internal class WebViewLogEventSerializer(
    private val dataConstraints: DataConstraints = DatadogDataConstraints()
) :
    Serializer<WebViewLogEvent> {

    override fun serialize(model: WebViewLogEvent): String {
        return sanitizeTagsAndAttributes(model).toJson().toString()
    }

    private fun sanitizeTagsAndAttributes(log: WebViewLogEvent): WebViewLogEvent {
        val sanitizedTags = dataConstraints
            .validateTags(log.ddtags.split(","))
            .joinToString(",")
        val sanitizedAttributes = dataConstraints
            .validateAttributes(log.additionalProperties)
            .filterKeys { it.isNotBlank() }
        return log.copy(
            ddtags = sanitizedTags,
            additionalProperties = sanitizedAttributes
        )
    }
}
