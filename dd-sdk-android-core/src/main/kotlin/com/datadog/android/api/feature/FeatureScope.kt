/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.core.metrics.PerformanceMetric
import com.datadog.android.core.metrics.TelemetryMetricType

/**
 * Represents a Datadog feature.
 */
interface FeatureScope {
    /**
     * Utility to write an event, asynchronously.
     * @param forceNewBatch if `true` forces the [EventBatchWriter] to write in a new file and
     * not reuse the already existing pending data persistence file. By default it is `false`.
     * @param callback an operation called with an up-to-date [DatadogContext]
     * and an [EventBatchWriter]. Callback will be executed on a worker thread from I/O pool.
     * [DatadogContext] will have a state created at the moment this method is called, before the
     * thread switch for the callback invocation.
     */
    fun withWriteContext(
        forceNewBatch: Boolean = false,
        callback: (DatadogContext, EventBatchWriter) -> Unit
    )

    /**
     * Send event to a given feature. It will be sent in a synchronous way.
     *
     * @param event Event to send.
     */
    fun sendEvent(event: Any)

    /**
     * Returns the original feature.
     */
    fun <T : Feature> unwrap(): T

    /**
     * Start measuring a performance metric.
     *
     * @param callerClass  name of the class calling the performance measurement.
     * @param metric name of the metric that we want to measure.
     * @param samplingRate value between 0-100 for sampling the event.
     * @return a PerformanceMetric object that can later be used to send telemetry, or null if sampled out
     */
    fun startPerformanceMeasure(
        callerClass: String,
        metric: TelemetryMetricType,
        samplingRate: Float
    ): PerformanceMetric?
}
