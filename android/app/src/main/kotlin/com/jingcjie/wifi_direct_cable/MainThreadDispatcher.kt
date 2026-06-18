package com.jingcjie.wifi_direct_cable

import android.os.Handler
import android.os.Looper

class MainThreadDispatcher internal constructor(
    private val isMainThread: () -> Boolean,
    private val postToMain: (Runnable) -> Unit
) {
    private constructor(handler: Handler) : this(
        isMainThread = { Looper.myLooper() == handler.looper },
        postToMain = { runnable ->
            handler.post(runnable)
            Unit
        }
    )

    constructor() : this(Handler(Looper.getMainLooper()))

    fun dispatch(action: () -> Unit) {
        val runnable = Runnable(action)
        if (isMainThread()) {
            runnable.run()
        } else {
            postToMain(runnable)
        }
    }
}
