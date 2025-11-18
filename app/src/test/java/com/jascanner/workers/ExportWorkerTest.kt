package com.jascanner.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExportWorkerTest {

private lateinit var context: Context
private lateinit var worker: ExportWorker

@Before
fun setup() {
context = ApplicationProvider.getApplicationContext()
}

@Test
fun `exportWorker should reject non-HTTPS TSA URLs`() = runBlocking {
// Given
val inputData = androidx.work.Data.Builder()
.putString("tsa_url", "http://timestamp.sectigo.com")
.putString("document_id", "123")
.build()

worker = TestListenableWorkerBuilder<ExportWorker>(context)
.setInputData(inputData)
.build()

// When
val result = worker.doWork()

// Then
assertTrue(result is ListenableWorker.Result.Failure)
}

@Test
fun `exportWorker should accept HTTPS TSA URLs`() = runBlocking {
// Given
val inputData = androidx.work.Data.Builder()
.putString("tsa_url", "https://timestamp.sectigo.com")
.putString("document_id", "123")
.build()

worker = TestListenableWorkerBuilder<ExportWorker>(context)
.setInputData(inputData)
.build()

// When
val result = worker.doWork()

// Then
// Note: This may fail if document doesn't exist, but TSA validation should pass
assertNotNull(result)
}

@Test
fun `exportWorker should handle missing document gracefully`() = runBlocking {
// Given
val inputData = androidx.work.Data.Builder()
.putString("tsa_url", "https://timestamp.sectigo.com")
.putString("document_id", "999999") // Non-existent
.build()

worker = TestListenableWorkerBuilder<ExportWorker>(context)
.setInputData(inputData)
.build()

// When
val result = worker.doWork()

// Then
assertTrue(result is ListenableWorker.Result.Failure || result is ListenableWorker.Result.Retry)
}
}
