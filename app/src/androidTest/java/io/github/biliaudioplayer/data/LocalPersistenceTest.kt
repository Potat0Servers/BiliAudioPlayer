package io.github.biliaudioplayer.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.biliaudioplayer.store.PinStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.UUID

/** Every test has its own preference file and key alias; real account state is untouched. */
@RunWith(AndroidJUnit4::class)
class LocalPersistenceTest {
    private lateinit var context: Context
    private lateinit var prefix: String
    private lateinit var alias: String
    private lateinit var preferenceName: String

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefix = "isolated_test_${UUID.randomUUID()}_"
        alias = "${prefix}key"
        preferenceName = "${prefix}session"
    }

    @After fun cleanup() {
        context.deleteSharedPreferences(preferenceName)
        context.deleteSharedPreferences("${prefix}local_pins")
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }

    @Test fun keystoreSessionPersistsAsRandomizedCiphertext() {
        val fixture = "SESSDATA=synthetic-test-session; bili_jct=synthetic-test-csrf"
        val store = EncryptedSessionStore(context, preferenceName, alias)
        store.write(fixture)
        val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val first = preferences.getString("encrypted_cookie", null)!!
        assertFalse(first.contains("SESSDATA"))
        assertFalse(first.contains("synthetic-test"))
        assertEquals(fixture, EncryptedSessionStore(context, preferenceName, alias).read())
        store.write(fixture)
        val second = preferences.getString("encrypted_cookie", null)!!
        assertNotEquals(first, second)
        assertEquals(fixture, store.read())
        store.clear()
        assertEquals("", EncryptedSessionStore(context, preferenceName, alias).read())
    }

    @Test fun tamperedCiphertextIsDiscardedWithoutReturningPartialCredential() {
        val store = EncryptedSessionStore(context, preferenceName, alias)
        store.write("SESSDATA=synthetic-test-session")
        val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val encrypted = preferences.getString("encrypted_cookie", null)!!
        val changed = encrypted.dropLast(4) + "AAAA"
        assertTrue(preferences.edit().putString("encrypted_cookie", changed).commit())
        assertEquals("", store.read())
        assertFalse(preferences.contains("encrypted_cookie"))
    }

    @Test fun folderAndPartPinsStaySeparateAcrossAccountsAndInstances() {
        val isolatedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                context.getSharedPreferences(prefix + name, mode)
        }
        val pins = PinStore(isolatedContext)
        pins.toggleFolder(100, "folder-1")
        pins.togglePart(100, "BV1hv411e77b:306996376")
        pins.toggleFolder(200, "folder-2")
        val reopened = PinStore(isolatedContext)
        assertEquals(setOf("folder-1"), reopened.folders(100))
        assertEquals(setOf("folder-2"), reopened.folders(200))
        assertEquals(setOf("BV1hv411e77b:306996376"), reopened.parts(100))
        assertTrue(reopened.parts(200).isEmpty())
        reopened.toggleFolder(100, "folder-1")
        assertTrue(pins.folders(100).isEmpty())
        assertEquals(setOf("folder-2"), pins.folders(200))
        assertEquals(setOf("BV1hv411e77b:306996376"), pins.parts(100))
    }
}
