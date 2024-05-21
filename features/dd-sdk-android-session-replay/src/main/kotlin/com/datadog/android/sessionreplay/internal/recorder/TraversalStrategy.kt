/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

internal enum class TraversalStrategy {
    TRAVERSE_ALL_CHILDREN,
    STOP_AND_RETURN_NODE,
    STOP_AND_DROP_NODE
}
