/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import androidx.annotation.WorkerThread
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.log.LogAttributes
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.context.DatadogContext
import com.datadog.android.v2.core.storage.DataWriter
import com.datadog.android.webview.internal.WebViewEventConsumer
import com.datadog.android.webview.internal.rum.WebViewRumEventContextProvider
import com.google.gson.JsonObject

internal class WebViewLogEventConsumer(
    private val sdkCore: SdkCore,
    internal val userLogsWriter: DataWriter<JsonObject>,
    private val rumContextProvider: WebViewRumEventContextProvider
) : WebViewEventConsumer<Pair<JsonObject, String>> {

    @WorkerThread
    override fun consume(event: Pair<JsonObject, String>) {
        if (event.second == USER_LOG_EVENT_TYPE) {
            sdkCore.getFeature(WebViewLogsFeature.WEB_LOGS_FEATURE_NAME)
                ?.withWriteContext { datadogContext, eventBatchWriter ->
                    val rumContext = rumContextProvider.getRumContext(datadogContext)
                    val mappedEvent = map(event.first, datadogContext, rumContext)
                    userLogsWriter.write(eventBatchWriter, mappedEvent)
                }
        }
    }

    private fun map(
        event: JsonObject,
        datadogContext: DatadogContext,
        rumContext: RumContext?
    ): JsonObject {
        addDdTags(event, datadogContext)
        correctDate(event, datadogContext)
        if (rumContext != null) {
            event.addProperty(LogAttributes.RUM_APPLICATION_ID, rumContext.applicationId)
            event.addProperty(LogAttributes.RUM_SESSION_ID, rumContext.sessionId)
        }
        return event
    }

    private fun correctDate(event: JsonObject, datadogContext: DatadogContext) {
        try {
            event.get(DATE_KEY_NAME)?.asLong?.let {
                event.addProperty(
                    DATE_KEY_NAME,
                    it + datadogContext.time.serverTimeOffsetMs
                )
            }
        } catch (e: ClassCastException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                JSON_PARSING_ERROR_MESSAGE,
                e
            )
        } catch (e: IllegalStateException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                JSON_PARSING_ERROR_MESSAGE,
                e
            )
        } catch (e: NumberFormatException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                JSON_PARSING_ERROR_MESSAGE,
                e
            )
        } catch (e: UnsupportedOperationException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                JSON_PARSING_ERROR_MESSAGE,
                e
            )
        }
    }

    private fun addDdTags(event: JsonObject, datadogContext: DatadogContext) {
        val sdkDdTags = "${LogAttributes.APPLICATION_VERSION}:${datadogContext.version}" +
            ",${LogAttributes.ENV}:${datadogContext.env}"
        var eventDdTags: String? = null
        try {
            eventDdTags = event.get(DDTAGS_KEY_NAME)?.asString
        } catch (e: ClassCastException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                JSON_PARSING_ERROR_MESSAGE,
                e
            )
        } catch (e: IllegalStateException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                JSON_PARSING_ERROR_MESSAGE,
                e
            )
        } catch (e: UnsupportedOperationException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                JSON_PARSING_ERROR_MESSAGE,
                e
            )
        }
        if (eventDdTags.isNullOrEmpty()) {
            event.addProperty(DDTAGS_KEY_NAME, sdkDdTags)
        } else {
            event.addProperty(DDTAGS_KEY_NAME, sdkDdTags + DDTAGS_SEPARATOR + eventDdTags)
        }
    }

    companion object {
        const val DDTAGS_SEPARATOR = ","
        const val DDTAGS_KEY_NAME = "ddtags"
        const val DATE_KEY_NAME = "date"
        const val USER_LOG_EVENT_TYPE = "log"
        const val INTERNAL_LOG_EVENT_TYPE = "internal_log"
        const val JSON_PARSING_ERROR_MESSAGE = "The bundled web log event could not be deserialized"
        val LOG_EVENT_TYPES = setOf(USER_LOG_EVENT_TYPE)
    }
}
