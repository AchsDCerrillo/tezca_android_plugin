package __PKG__.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder

/** Pattern for PreferencesDataSource tests: a real DataStore on a temp file, scoped to the test. */
class PreferencesDataStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `values written are read back`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(testScheduler),
            produceFile = { java.io.File(tmp.root, "test.preferences_pb") },
        )
        val key = stringPreferencesKey("session.user_id")

        dataStore.edit { it[key] = "42" }

        assertEquals("42", dataStore.data.first()[key])
    }
}
