/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.NoOpStringObfuscator
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.FixedLengthStringObfuscator
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class TextViewWireframeMapperTest : BaseTextViewWireframeMapperTest() {

    @Test
    fun `M use the NoOpObfuscator as defaultObfuscator when initialized`() {
        // When
        val textViewMapper = TextWireframeMapper()

        // Then
        assertThat(textViewMapper.defaultStringObfuscator)
            .isInstanceOf(NoOpStringObfuscator::class.java)
    }

    @Test
    fun `M use the StaticStringObfuscator as extraSensibleStringsObfuscator when initialized`() {
        // When
        val textViewMapper = TextWireframeMapper()

        // Then
        assertThat(textViewMapper.extraSensibleStringsObfuscator)
            .isInstanceOf(FixedLengthStringObfuscator::class.java)
    }
}
