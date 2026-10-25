package com.example.minedtv.data

import android.content.Context
import com.example.minedtv.data.search.FakeSearchRepository
import com.example.minedtv.data.search.SearchRepository
import com.example.minedtv.data.share.EnhanceRepository
import com.example.minedtv.data.share.FakeEnhanceRepository
import com.example.minedtv.data.share.FakePlatformPublisher
import com.example.minedtv.data.share.IntentPlatformPublisher
import com.example.minedtv.data.share.PlatformPublisher
import com.example.minedtv.data.api.MinedTvApi
import com.example.minedtv.data.search.RemoteSearchRepository
import com.example.minedtv.data.share.RemoteEnhanceRepository

/** Poor man's DI. Swap the fakes for server-backed implementations when the backend is ready. */
object AppServices {
    var enhanceRepository: EnhanceRepository = FakeEnhanceRepository()
    var platformPublisher: PlatformPublisher = FakePlatformPublisher()
    var searchRepository: SearchRepository = FakeSearchRepository()

    fun init(context: Context) {
        // Real hand-off to the TikTok / Instagram apps (no platform developer setup needed).
        platformPublisher = IntentPlatformPublisher(context.applicationContext)
        // Real semantic search against the MinedTV backend when an API key is configured.
        if (MinedTvApi.isConfigured) searchRepository = RemoteSearchRepository()
        // Real AI edits (Gemini plan + on-device render); sample clips still use the simulation.
        enhanceRepository = RemoteEnhanceRepository(context.applicationContext)
    }
}
