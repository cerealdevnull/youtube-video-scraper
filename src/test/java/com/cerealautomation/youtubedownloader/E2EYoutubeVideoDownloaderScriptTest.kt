package com.cerealautomation.youtubedownloader

import com.cereal.sdk.ExecutionResult
import com.cereal.test.components.TestComponentProviderFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertIs
import kotlin.test.assertTrue

class E2EYoutubeVideoDownloaderScriptTest {

    @Test
    fun `end to end download test for short youtube video`() = runBlocking {
        // We use a known very short video: jNQXAC9IVRw ("Me at the zoo")
        // This takes less than a few seconds to download and avoids muxing.
        val script = YoutubeVideoDownloaderScript()

        val configuration = mockk<YoutubeDownloaderConfiguration> {
            every { videoUrl() } returns "jNQXAC9IVRw"
            every { quality() } returns DownloadQuality.Q360P 
        }

        val componentProviderFactory = TestComponentProviderFactory()
        val provider = componentProviderFactory.create()

        script.onStart(configuration, provider)
        val result = script.execute(configuration, provider) { message ->
            println("Status Update: $message")
        }

        if (result is ExecutionResult.Error) {
            println("Test Failed with Error: ${result.message}")
        }
        
        assertIs<ExecutionResult.Success>(result)
        
        // Extract the saved filename from the success message
        val successMessage = result.message
        assertTrue(successMessage.startsWith("Saved to Downloads folder: "), "Expected success message to start with 'Saved to Downloads folder: ' but got: $successMessage")
        
        val filename = successMessage.removePrefix("Saved to Downloads folder: ")
        
        val downloadsDir = File(System.getProperty("user.home"), "Downloads")
        val downloadedFile = File(downloadsDir, filename)
        
        assertTrue(downloadedFile.exists(), "Downloaded file should exist at ${downloadedFile.absolutePath}")
        assertTrue(downloadedFile.length() > 0, "Downloaded file should not be empty")

        // Clean up
        downloadedFile.delete()
        Unit
    }
}
