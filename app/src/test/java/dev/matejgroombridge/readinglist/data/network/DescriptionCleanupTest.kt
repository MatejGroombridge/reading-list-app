package dev.matejgroombridge.readinglist.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DescriptionCleanupTest {

    @Test
    fun `keeps the prose and drops links and see-also blocks`() {
        val raw = "A novel by [Albert Camus][1] about [Meursault](https://x.y).\n\n\n\nIt is short.\n" +
            "----------\nSee also: [Other edition][2]\n\n[1]: https://a\n[2]: https://b"
        assertEquals(
            "A novel by Albert Camus about Meursault.\n\nIt is short.",
            BookLookup.cleanDescription(raw),
        )
    }
}
