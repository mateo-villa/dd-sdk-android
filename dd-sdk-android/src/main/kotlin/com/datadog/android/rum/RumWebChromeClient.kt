/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import com.datadog.android.log.Logger

/**
 * A [WebViewClient] propagating all relevant events to Datadog.
 *
 * Any console message will be forwarded to an internal [Logger], and errors
 * will be sent to the [GlobalRum] monitor as Rum Errors.
 */
open class RumWebChromeClient
internal constructor(private val logger: Logger) : WebChromeClient() {

    constructor() : this(
        Logger.Builder()
            .setLoggerName(LOGGER_NAME)
            .setNetworkInfoEnabled(true)
            .build()
    )

    // region WebChromeClient

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        if (consoleMessage != null) {
            val message = consoleMessage.message()
            val level = consoleMessage.messageLevel()
            val attributes = mapOf<String, Any>(
                SOURCE_ID to consoleMessage.sourceId(),
                SOURCE_LINE to consoleMessage.lineNumber()
            )

            logger.log(level.toLogLevel(), message, null, attributes)

            if (level == ConsoleMessage.MessageLevel.ERROR) {
                GlobalRum.get().addError(message, LOGGER_NAME, null, attributes)
            }
        }
        return false
    }

    // endregion

    // region Internal

    private fun ConsoleMessage.MessageLevel.toLogLevel(): Int {
        return when (this) {
            ConsoleMessage.MessageLevel.LOG -> Log.VERBOSE
            ConsoleMessage.MessageLevel.DEBUG -> Log.DEBUG
            ConsoleMessage.MessageLevel.TIP -> Log.INFO
            ConsoleMessage.MessageLevel.WARNING -> Log.WARN
            ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
        }
    }

    // endregion

    companion object {
        internal const val LOGGER_NAME = "WebChromeClient"

        internal const val SOURCE_ID = "source.id"
        internal const val SOURCE_LINE = "source.line"
    }
}