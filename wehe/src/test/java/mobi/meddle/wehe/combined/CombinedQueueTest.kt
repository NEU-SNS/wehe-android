package mobi.meddle.wehe.combined

import com.google.common.truth.Truth.assertThat
import mobi.meddle.wehe.data.model.ServerInstance
import mobi.meddle.wehe.data.model.UDPReplayInfoBean
import mobi.meddle.wehe.data.model.UpdateUIBean
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Tests for CombinedQueue (mobi.meddle.wehe.combined.CombinedQueue.kt, main sources).
 * <p>
 * CombinedQueue.run() is the orchestrator that starts real Threads to send replay packets; most
 * of its internals (getRecvSemaLock, nextTCP, nextUDP) are `private fun`, so they aren't callable
 * directly from a test in the same package (Kotlin `private` is class-private, not
 * package-private like un-annotated Java members). These tests therefore exercise:
 *  (a) the publicly-visible ABORT/abort_reason/threads state (used across threads by
 *      CTCPClientThread via `synchronized(queue) { ... }`, per CTCPClientThread.java),
 *  (b) run() end-to-end with a trivial (empty) packet queue, which is fast and deterministic,
 *  (c) stopTimers() as a no-op when nothing was ever scheduled.
 */
class CombinedQueueTest {

    private fun emptyQueue(timeout: Int = 30): CombinedQueue =
        CombinedQueue(ArrayList(), ArrayList(), ArrayList(), timeout)

    @Test
    fun defaults_abortFalse_reasonNull_threadsZero() {
        val queue = emptyQueue()

        assertThat(queue.ABORT).isFalse()
        assertThat(queue.abort_reason).isNull()
        assertThat(queue.threads).isEqualTo(0)
    }

    @Test
    fun abortAndReason_areSettableIndependently() {
        val queue = emptyQueue()

        queue.ABORT = true
        queue.abort_reason = "custom reason"

        assertThat(queue.ABORT).isTrue()
        assertThat(queue.abort_reason).isEqualTo("custom reason")
    }

    @Test
    fun stopTimers_withNoScheduledTimers_isANoOp() {
        val queue = emptyQueue()
        queue.stopTimers() // must not throw even though `timers` is empty
    }

    @Test
    fun threadsCounter_concurrentSynchronizedUpdates_areAllAppliedCorrectly() {
        // Mirrors the exact pattern production code uses: every access to `threads` is wrapped in
        // `synchronized(queue) { ... }` (see CTCPClientThread.java's finally block, and
        // CombinedQueue.kt's own nextTCP()/run()). `threads` itself is a plain (non-@Volatile) Int
        // property - unlike ABORT/abort_reason, which ARE @Volatile - so its thread-safety relies
        // entirely on every caller following the synchronized(queue) convention. This test proves
        // that convention, when actually followed by every thread (as production code does), is
        // sufficient: no updates are lost across many real concurrent threads.
        val queue = emptyQueue()
        val threadCount = 8
        val incrementsPerThread = 500

        val threads = (1..threadCount).map {
            Thread {
                repeat(incrementsPerThread) {
                    synchronized(queue) {
                        queue.threads = queue.threads + 1
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }

        assertThat(queue.threads).isEqualTo(threadCount * incrementsPerThread)
    }

    @Test
    fun run_withEmptyPacketQueueAndOneServer_completesQuicklyWithoutError() {
        // q is empty, so the per-server `sendPckts` runnable's `for (RS in q)` loop body never
        // executes, and no CTCPClientThreads are ever spawned - this exercises run()'s thread
        // start/join bookkeeping end-to-end (real Threads) without needing a network or any
        // RequestSet fixtures, and should return in well under a second.
        val queue = emptyQueue()
        val updateUIBean = UpdateUIBean()

        queue.run(
            updateUIBean,
            1,
            ArrayList<HashMap<String, CTCPClient>>(),
            ArrayList<HashMap<String, CUDPClient>>(),
            ArrayList<UDPReplayInfoBean>(),
            ArrayList<HashMap<String, HashMap<String, ServerInstance>>>(),
            false,
            arrayListOf("server1"),
            EmptyCoroutineContext
        )

        assertThat(queue.ABORT).isFalse()
        assertThat(updateUIBean.progress).isEqualTo(0)
    }
}
