package __PKG__.core.common.result

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class DataResultTest {

    @Test
    fun `map transforms success and keeps failure`() {
        assertEquals(DataResult.Success(4), DataResult.Success(2).map { it * 2 })
        val failure = DataResult.Failure(AppError.Network.Timeout)
        assertEquals(failure, (failure as DataResult<Int>).map { it * 2 })
    }

    @Test
    fun `localCall maps exceptions to a database error`() {
        val result = localCall { error("disk full") }
        assertIs<AppError.Local.Database>(result.errorOrNull())
    }

    @Test
    fun `localCall rethrows cancellation`() {
        assertFailsWith<CancellationException> { localCall { throw CancellationException("cancelled") } }
    }
}
