/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

import com.datadog.gradle.config.AndroidConfig
import com.datadog.gradle.config.androidLibraryConfig
import com.datadog.gradle.config.dependencyUpdateConfig
import com.datadog.gradle.config.java17
import com.datadog.gradle.config.junitConfig
import com.datadog.gradle.config.kotlinConfig

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.github.ben-manes.versions")
}

android {
    defaultConfig {
        compileSdk = AndroidConfig.TARGET_SDK
        minSdk = AndroidConfig.MIN_SDK
    }
    namespace = "com.datadog.tools.benchmark"
    compileOptions {
        java17()
    }
}

dependencies {
    implementation(project(":dd-sdk-android-core"))
    implementation(libs.kotlin)
    implementation(libs.okHttp)
    implementation(libs.openTelemetryApiBenchmark)
    implementation(libs.openTelemetrySdkBenchmark)
    implementation(libs.gson)
    testImplementation(project(":tools:unit")) {
        attributes {
            attribute(
                com.android.build.api.attributes.ProductFlavorAttr.of("platform"),
                objects.named("jvm")
            )
        }
    }

    testImplementation(libs.bundles.jUnit5)
    testImplementation(libs.bundles.testTools)
}

apply(from = "generate_trace_models.gradle.kts")

kotlinConfig()
junitConfig()
dependencyUpdateConfig()
androidLibraryConfig()
