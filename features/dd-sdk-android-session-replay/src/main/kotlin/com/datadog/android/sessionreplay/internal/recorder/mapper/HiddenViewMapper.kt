/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.Context
import android.view.View
import androidx.core.content.ContextCompat
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

class HiddenViewMapper(viewIdentifierResolver: ViewIdentifierResolver,
                       colorStringFormatter: ColorStringFormatter,
                       viewBoundsResolver: ViewBoundsResolver,
                       drawableToColorMapper: DrawableToColorMapper) : BaseWireframeMapper<View>(
        viewIdentifierResolver,
        colorStringFormatter,
        viewBoundsResolver,
        drawableToColorMapper
) {
    override fun map(view: View, mappingContext: MappingContext,
                     asyncJobStatusCallback: AsyncJobStatusCallback,
                     internalLogger: InternalLogger): List<MobileSegment.Wireframe> {
        val pixelsDensity = mappingContext.systemInformation.screenDensity
        val wireframes = mutableListOf<MobileSegment.Wireframe>()
        val viewGlobalBounds = viewBoundsResolver.resolveViewGlobalBounds(view, pixelsDensity)
        val trackId = viewIdentifierResolver.resolveChildUniqueIdentifier(view, SwitchCompatMapper.TRACK_KEY_NAME)
        val shapeStyle = resolveShapeStyle(view.context)
        if (trackId != null) {
            val trackWireframe = MobileSegment.Wireframe.ShapeWireframe(
                    id = trackId,
                    x = viewGlobalBounds.x,
                    y = viewGlobalBounds.y,
                    width = viewGlobalBounds.width,
                    height = viewGlobalBounds.height,
                    border = null,
                    shapeStyle = shapeStyle
            )
            wireframes.add(trackWireframe)
        }

        return wireframes
    }

    private fun resolveShapeStyle(context: Context): MobileSegment.ShapeStyle {
        val color = ContextCompat.getColor(context, android.R.color.darker_gray)
        return MobileSegment.ShapeStyle(
                backgroundColor = colorStringFormatter.formatColorAsHexString(color)
        )
    }
}