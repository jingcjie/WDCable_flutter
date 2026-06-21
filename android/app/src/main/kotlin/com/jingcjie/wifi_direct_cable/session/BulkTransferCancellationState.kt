package com.jingcjie.wifi_direct_cable.session

import java.util.concurrent.atomic.AtomicBoolean

internal class BulkTransferCancellationState(
    private val cancellationRequested: () -> Boolean
) {
    private val startSent = AtomicBoolean(false)
    private val cancelFrameClaimed = AtomicBoolean(false)

    fun markStartSent() {
        startSent.set(true)
    }

    fun isCancellationRequested(): Boolean = cancellationRequested()

    fun claimCancelFrame(): Boolean {
        return startSent.get() && cancelFrameClaimed.compareAndSet(false, true)
    }
}
