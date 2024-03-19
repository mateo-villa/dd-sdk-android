/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskInputTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal class MaskInputTabWireframeMapper internal constructor(
    viewIdentifierResolver: ViewIdentifierResolver,
    viewBoundsResolver: ViewBoundsResolver,
    textViewMapper: WireframeMapper<TextView, MobileSegment.Wireframe>
) : TabWireframeMapper(
    viewIdentifierResolver,
    viewBoundsResolver,
    textViewMapper
) {

    constructor(
        viewIdentifierResolver: ViewIdentifierResolver,
        colorStringFormatter: ColorStringFormatter,
        viewBoundsResolver: ViewBoundsResolver,
        drawableToColorMapper: DrawableToColorMapper
    ) : this(
        viewIdentifierResolver,
        viewBoundsResolver,
        MaskInputTextViewMapper(
            viewIdentifierResolver,
            colorStringFormatter,
            viewBoundsResolver,
            drawableToColorMapper
        )
    )
}
