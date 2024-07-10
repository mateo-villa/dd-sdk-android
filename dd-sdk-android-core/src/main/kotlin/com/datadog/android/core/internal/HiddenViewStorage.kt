/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal

import android.view.View

object HiddenViewStorage {

    val hiddenViewIds: MutableSet<Int> = hashSetOf()


    fun hidde(view: View) {
        hiddenViewIds.add(view.id)
    }

    fun show(view: View) {
        hiddenViewIds.remove(view.id)
    }
}