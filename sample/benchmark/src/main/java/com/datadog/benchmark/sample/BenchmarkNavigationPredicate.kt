/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample

import androidx.navigation.NavDestination
import com.datadog.android.rum.tracking.ComponentPredicate

internal class BenchmarkNavigationPredicate : ComponentPredicate<NavDestination> {
    override fun accept(component: NavDestination): Boolean {
        return true
    }

    override fun getViewName(component: NavDestination): String {
        return component.label.toString()
    }
}
