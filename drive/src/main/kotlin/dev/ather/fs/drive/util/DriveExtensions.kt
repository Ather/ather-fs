package dev.ather.fs.drive.util

import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.drive.DriveRequest
import dev.ather.fs.drive.DriveEnvironment
import java.util.concurrent.TimeUnit

internal val DEFAULT_BACKOFF = ExponentialBackOff.Builder()
    .setInitialIntervalMillis(500)
    .setMaxElapsedTimeMillis(TimeUnit.MINUTES.toMillis(5).toInt())
    .setMaxIntervalMillis(TimeUnit.MINUTES.toMillis(1).toInt())
    .setMultiplier(1.5)
    .setRandomizationFactor(0.5)
    .build()

internal fun <T> DriveRequest<T>.executeBackoff(
    env: DriveEnvironment,
    backoff: ExponentialBackOff = DEFAULT_BACKOFF
): T = this.buildHttpRequest()
    .setReadTimeout(env.readTimeoutMillis)
    .apply {
        if (env.exponentialBackoff) {
            unsuccessfulResponseHandler = HttpBackOffUnsuccessfulResponseHandler(backoff)
        }
        numberOfRetries = 3
    }
    .execute()
    .parseAs(this.responseClass)
