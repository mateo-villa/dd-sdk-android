/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload.v2

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.core.internal.ContextProvider
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.core.internal.data.upload.UploadStatus
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.persistence.BatchConfirmation
import com.datadog.android.core.internal.persistence.BatchId
import com.datadog.android.core.internal.persistence.BatchReader
import com.datadog.android.core.internal.persistence.Storage
import com.datadog.android.core.internal.system.SystemInfo
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataUploadRunnableTest {

    @Mock
    lateinit var mockThreadPoolExecutor: ScheduledThreadPoolExecutor

    @Mock
    lateinit var mockStorage: Storage

    @Mock
    lateinit var mockDataUploader: DataUploader

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockSystemInfoProvider: SystemInfoProvider

    @Mock
    lateinit var mockContextProvider: ContextProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeContext: DatadogContext

    @Forgery
    lateinit var fakeDataUploadConfiguration: DataUploadConfiguration

    private lateinit var testedRunnable: DataUploadRunnable

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeNetworkInfo =
            NetworkInfo(
                forge.aValueFrom(
                    enumClass = NetworkInfo.Connectivity::class.java,
                    exclude = listOf(NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED)
                )
            )
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        val fakeSystemInfo = SystemInfo(
            batteryFullOrCharging = true,
            batteryLevel = forge.anInt(min = 20, max = 100),
            powerSaveMode = false,
            onExternalPowerSource = true
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        whenever(mockContextProvider.context) doReturn fakeContext

        testedRunnable = DataUploadRunnable(
            mockThreadPoolExecutor,
            mockStorage,
            mockDataUploader,
            mockContextProvider,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            fakeDataUploadConfiguration,
            TEST_BATCH_UPLOAD_WAIT_TIMEOUT_MS,
            mockInternalLogger
        )
    }

    @Test
    fun `doesn't send batch when offline`() {
        // Given
        val networkInfo = NetworkInfo(
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED
        )
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn networkInfo

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockDataUploader, mockStorage)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M send batch W run() { batteryFullOrCharging }`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @IntForgery(min = 0, max = DataUploadRunnable.LOW_BATTERY_THRESHOLD) batteryLevel: Int,
        forge: Forge
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            batteryFullOrCharging = true,
            batteryLevel = batteryLevel,
            onExternalPowerSource = false,
            powerSaveMode = false
        )

        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }

        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn forge.getForgery(UploadStatus.Success::class.java)

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(true)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M send batch W run() { battery level high }`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @IntForgery(min = DataUploadRunnable.LOW_BATTERY_THRESHOLD + 1) batteryLevel: Int,
        forge: Forge
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            batteryLevel = batteryLevel,
            batteryFullOrCharging = false,
            onExternalPowerSource = false,
            powerSaveMode = false
        )
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }

        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn forge.getForgery(UploadStatus.Success::class.java)

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(true)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M send batch W run() { onExternalPower }`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @IntForgery(min = 0, max = DataUploadRunnable.LOW_BATTERY_THRESHOLD) batteryLevel: Int,
        forge: Forge
    ) {
        val fakeSystemInfo = SystemInfo(
            onExternalPowerSource = true,
            batteryLevel = batteryLevel,
            batteryFullOrCharging = false,
            powerSaveMode = false
        )
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }

        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn forge.getForgery(UploadStatus.Success::class.java)

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(true)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not send batch W run() { not enough battery }`(
        @IntForgery(min = 0, max = DataUploadRunnable.LOW_BATTERY_THRESHOLD) batteryLevel: Int
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            batteryLevel = batteryLevel,
            batteryFullOrCharging = false,
            onExternalPowerSource = false,
            powerSaveMode = false
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockStorage, mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not send batch W run() { batteryFullOrCharging, powerSaveMode }`(
        @IntForgery(min = 0, max = 100) batteryLevel: Int
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            batteryFullOrCharging = true,
            powerSaveMode = true,
            batteryLevel = batteryLevel,
            onExternalPowerSource = false
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockStorage, mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not send batch W run() { batteryLeveHigh, powerSaveMode }`(
        @IntForgery(min = DataUploadRunnable.LOW_BATTERY_THRESHOLD + 1) batteryLevel: Int
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            batteryLevel = batteryLevel,
            powerSaveMode = true,
            batteryFullOrCharging = false,
            onExternalPowerSource = false
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockStorage, mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `M not send batch W run() { onExternalPower, powerSaveMode }`(
        @IntForgery(min = 0, max = DataUploadRunnable.LOW_BATTERY_THRESHOLD) batteryLevel: Int
    ) {
        // Given
        val fakeSystemInfo = SystemInfo(
            onExternalPowerSource = true,
            powerSaveMode = true,
            batteryLevel = batteryLevel,
            batteryFullOrCharging = false
        )
        whenever(mockSystemInfoProvider.getLatestSystemInfo()) doReturn fakeSystemInfo

        // When
        testedRunnable.run()

        // Then
        verifyNoInteractions(mockStorage, mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 no batch to send`() {
        // Given
        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            it.getArgument<() -> Unit>(0)()
        }

        // When
        testedRunnable.run()

        // Then
        verify(mockStorage).readNextBatch(any(), any())
        verifyNoMoreInteractions(mockStorage)
        verifyNoInteractions(mockDataUploader)
        verify(mockThreadPoolExecutor).schedule(
            eq(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `batch sent successfully`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn forge.getForgery(UploadStatus.Success::class.java)

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(true)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @MethodSource("retryBatchStatusValues")
    fun `batch kept on error`(
        uploadStatus: UploadStatus,
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn uploadStatus

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(false)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @MethodSource("retryBatchStatusValues")
    fun `batch kept after n errors`(
        uploadStatus: UploadStatus,
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @IntForgery(min = 3, max = 42) runCount: Int,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn uploadStatus

        // WHen
        repeat(runCount) {
            testedRunnable.run()
        }

        // Then
        verify(batchConfirmation, times(runCount)).markAsRead(false)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader, times(runCount)).read()
        verify(mockDataUploader, times(runCount)).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor, times(runCount)).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @ParameterizedTest
    @MethodSource("dropBatchStatusValues")
    fun `batch dropped on error`(
        uploadStatus: UploadStatus,
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn uploadStatus

        // When
        testedRunnable.run()

        // Then
        verify(batchConfirmation).markAsRead(true)
        verifyNoMoreInteractions(batchConfirmation)
        verify(batchReader).read()
        verify(mockDataUploader).upload(fakeContext, batchData, batchMetadata)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `when has batches the upload frequency will increase`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn forge.getForgery(UploadStatus.Success::class.java)

        // When
        repeat(5) {
            testedRunnable.run()
        }

        // Then
        val captor = argumentCaptor<Long>()
        verify(mockThreadPoolExecutor, times(5))
            .schedule(same(testedRunnable), captor.capture(), eq(TimeUnit.MILLISECONDS))
        captor.allValues.reduce { previous, next ->
            assertThat(next).isLessThan(previous)
            next
        }
    }

    @Test
    fun `𝕄 reduce delay between runs 𝕎 upload is successful`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @IntForgery(16, 64) runCount: Int,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn forge.getForgery(UploadStatus.Success::class.java)

        // When
        repeat(runCount) {
            testedRunnable.run()
        }

        // Then
        argumentCaptor<Long> {
            verify(mockThreadPoolExecutor, times(runCount))
                .schedule(
                    same(testedRunnable),
                    capture(),
                    eq(TimeUnit.MILLISECONDS)
                )

            allValues.reduce { previous, next ->
                assertThat(next)
                    .isLessThanOrEqualTo(previous)
                    .isBetween(testedRunnable.minDelayMs, testedRunnable.maxDelayMs)
                next
            }
        }
    }

    @ParameterizedTest
    @MethodSource("dropBatchStatusValues")
    fun `𝕄 reduce delay between runs 𝕎 batch fails and should be dropped`(
        uploadStatus: UploadStatus,
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        @IntForgery(16, 64) runCount: Int,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn uploadStatus

        // When
        repeat(runCount) {
            testedRunnable.run()
        }

        // Then
        argumentCaptor<Long> {
            verify(mockThreadPoolExecutor, times(runCount))
                .schedule(
                    same(testedRunnable),
                    capture(),
                    eq(TimeUnit.MILLISECONDS)
                )

            allValues.reduce { previous, next ->
                assertThat(next)
                    .isLessThanOrEqualTo(previous)
                    .isBetween(testedRunnable.minDelayMs, testedRunnable.maxDelayMs)
                next
            }
        }
    }

    @Test
    fun `𝕄 increase delay between runs 𝕎 no batch available`(
        @IntForgery(16, 64) runCount: Int
    ) {
        // When
        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            it.getArgument<() -> Unit>(0).invoke()
        }

        repeat(runCount) {
            testedRunnable.run()
        }

        // Then
        argumentCaptor<Long> {
            verify(mockThreadPoolExecutor, times(runCount))
                .schedule(
                    same(testedRunnable),
                    capture(),
                    eq(TimeUnit.MILLISECONDS)
                )

            allValues.reduce { previous, next ->
                assertThat(next)
                    .isGreaterThanOrEqualTo(previous)
                    .isBetween(testedRunnable.minDelayMs, testedRunnable.maxDelayMs)
                next
            }
        }
    }

    @ParameterizedTest
    @MethodSource("retryBatchStatusValues")
    fun `𝕄 increase delay between runs 𝕎 batch fails and should be retried`(
        status: UploadStatus,
        @IntForgery(16, 64) runCount: Int,
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchConfirmation = mock<BatchConfirmation>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            whenever(mockStorage.confirmBatchRead(eq(batchId), any(), any())) doAnswer {
                it.getArgument<(BatchConfirmation) -> Unit>(2).invoke(batchConfirmation)
            }
            it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
        }
        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doReturn status

        // When
        repeat(runCount) {
            testedRunnable.run()
        }

        // Then
        argumentCaptor<Long> {
            verify(mockThreadPoolExecutor, times(runCount))
                .schedule(
                    same(testedRunnable),
                    capture(),
                    eq(TimeUnit.MILLISECONDS)
                )

            allValues.reduce { previous, next ->
                assertThat(next)
                    .isGreaterThanOrEqualTo(previous)
                    .isBetween(testedRunnable.minDelayMs, testedRunnable.maxDelayMs)
                next
            }
        }
    }

    // region async

    @Test
    fun `𝕄 respect batch wait upload timeout 𝕎 run()`() {
        // Given
        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            // imitate async which never completes
        }

        // When
        testedRunnable.run()

        // Then
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `𝕄 stop waiting 𝕎 run() { exception is thrown }`(
        @StringForgery batch: List<String>,
        @StringForgery batchMeta: String,
        forge: Forge
    ) {
        // Given
        val batchId = mock<BatchId>()
        val batchReader = mock<BatchReader>()
        val batchData = batch.map { it.toByteArray() }
        val batchMetadata = forge.aNullable { batchMeta.toByteArray() }

        whenever(batchReader.read()) doReturn batchData
        whenever(batchReader.currentMetadata()) doReturn batchMetadata

        whenever(mockStorage.readNextBatch(any(), any())) doAnswer {
            Thread {
                it.getArgument<(BatchId, BatchReader) -> Unit>(1).invoke(batchId, batchReader)
            }.start()
        }

        whenever(
            mockDataUploader.upload(
                fakeContext,
                batchData,
                batchMetadata
            )
        ) doThrow forge.aThrowable()

        // When
        val start = System.currentTimeMillis()
        testedRunnable.run()

        // Then
        assertThat(System.currentTimeMillis() - start)
            .isLessThan(TEST_BATCH_UPLOAD_WAIT_TIMEOUT_MS)
        verify(mockThreadPoolExecutor).schedule(
            same(testedRunnable),
            any(),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    // endregion

    companion object {
        const val TEST_BATCH_UPLOAD_WAIT_TIMEOUT_MS = 100L

        @JvmStatic
        fun retryBatchStatusValues(): List<UploadStatus> {
            val forge = Forge().apply {
                Configurator().configure(this)
            }

            return listOf(
                forge.getForgery(UploadStatus.HttpServerError::class.java),
                forge.getForgery(UploadStatus.HttpClientRateLimiting::class.java),
                forge.getForgery(UploadStatus.NetworkError::class.java)
            )
        }

        @JvmStatic
        fun dropBatchStatusValues(): List<UploadStatus> {
            val forge = Forge().apply {
                Configurator().configure(this)
            }

            return listOf(
                forge.getForgery(UploadStatus.HttpClientError::class.java),
                forge.getForgery(UploadStatus.HttpRedirection::class.java),
                forge.getForgery(UploadStatus.UnknownError::class.java),
                forge.getForgery(UploadStatus.UnknownStatus::class.java)
            )
        }
    }
}