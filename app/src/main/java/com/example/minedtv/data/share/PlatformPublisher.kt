package com.example.minedtv.data.share

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Posts a video to TikTok / Instagram. A real implementation would use TikTok's
 * Share Kit / Content Posting API and Instagram's sharing intent or Graph API,
 * or let the server post on the user's behalf.
 */
interface PlatformPublisher {
    fun publish(request: PublishRequest): Flow<PublishStatus>
}

class FakePlatformPublisher : PlatformPublisher {
    override fun publish(request: PublishRequest): Flow<PublishStatus> = flow {
        for (i in 1..20) {
            delay(100)
            emit(PublishStatus.Uploading(i / 20f))
        }
        emit(PublishStatus.Published("https://example.com/${request.platform.name.lowercase()}/post/${System.currentTimeMillis()}"))
    }
}
