package com.example.minedtv.data

import com.example.minedtv.data.share.EnhanceRepository
import com.example.minedtv.data.share.FakeEnhanceRepository
import com.example.minedtv.data.share.FakePlatformPublisher
import com.example.minedtv.data.share.PlatformPublisher

/** Poor man's DI. Swap the fakes for server-backed implementations when the backend is ready. */
object AppServices {
    var enhanceRepository: EnhanceRepository = FakeEnhanceRepository()
    var platformPublisher: PlatformPublisher = FakePlatformPublisher()
}
