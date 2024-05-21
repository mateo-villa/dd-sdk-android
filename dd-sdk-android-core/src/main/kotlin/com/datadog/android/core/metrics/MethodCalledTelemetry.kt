/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.metrics

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.metrics.PerformanceMetric.Companion.METRIC_TYPE

/**
 * Performance metric to measure the execution time for a method.
 * @param callerClass - the class calling the performance metric.
 * @param logger - an instance of the internal logger.
 * @param startTime - the time when the metric is instantiated, to be used as the start point for the measurement.
 */
class MethodCalledTelemetry(
    private val callerClass: String,
    private val logger: InternalLogger,
    private val startTime: Long = System.nanoTime()
) : PerformanceMetric {

    override fun stopAndSend(isSuccessful: Boolean) {
        val executionTime = System.nanoTime() - startTime
        val additionalProperties: MutableMap<String, Any> = mutableMapOf()

        additionalProperties[EXECUTION_TIME] = executionTime
        additionalProperties[OPERATION_NAME] = METHOD_CALL_OPERATION_NAME
        additionalProperties[CALLER_CLASS] = callerClass
        additionalProperties[IS_SUCCESSFUL] = isSuccessful
        additionalProperties[METRIC_TYPE] = METRIC_TYPE_VALUE

        logger.logMetric(
            messageBuilder = { METHOD_CALLED_METRIC_NAME },
            additionalProperties = additionalProperties
        )
    }

    companion object {
        /**
         * Title of the metric to be sent.
         */
        const val METHOD_CALLED_METRIC_NAME: String = "[Mobile Metric] Method Called"

        /**
         * Metric type value.
         */
        const val METRIC_TYPE_VALUE: String = "method called"

        /**
         * The key for operation name.
         */
        const val OPERATION_NAME: String = "operation_name"

        /**
         * The key for caller class.
         */
        const val CALLER_CLASS: String = "caller_class"

        /**
         * The key for is successful.
         */
        const val IS_SUCCESSFUL: String = "is_successful"

        /**
         * The key for execution time.
         */
        const val EXECUTION_TIME: String = "execution_time"

        /**
         * The value for operation name.
         */
        const val METHOD_CALL_OPERATION_NAME: String = "Capture Record"
    }
}
