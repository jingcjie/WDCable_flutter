package com.jingcjie.wifi_direct_cable

import org.junit.Assert.assertEquals
import org.junit.Test

class MainThreadDispatcherTest {
    @Test
    fun `dispatch executes inline on the main thread`() {
        var postCount = 0
        var executionCount = 0
        val dispatcher = MainThreadDispatcher(
            isMainThread = { true },
            postToMain = { postCount += 1 }
        )

        dispatcher.dispatch {
            executionCount += 1
        }

        assertEquals(1, executionCount)
        assertEquals(0, postCount)
    }

    @Test
    fun `dispatch posts worker action exactly once`() {
        val postedActions = mutableListOf<Runnable>()
        var executionCount = 0
        val dispatcher = MainThreadDispatcher(
            isMainThread = { false },
            postToMain = { postedActions += it }
        )

        dispatcher.dispatch {
            executionCount += 1
        }

        assertEquals(0, executionCount)
        assertEquals(1, postedActions.size)

        postedActions.single().run()

        assertEquals(1, executionCount)
    }
}
