package app.vela.ui

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * Laze is Google-free by design: no requests are made to Google backend endpoints.
 * [on] is permanently true.
 */
object GoogleFree {
    val on = mutableStateOf(true)
    val resolveLinks = mutableStateOf(true)

    fun setResolveLinks(context: Context, value: Boolean) {
        resolveLinks.value = value
    }

    fun init(context: Context) {
        on.value = true
        app.vela.core.data.NoGoogle.enabled = true
    }

    fun set(context: Context, value: Boolean) {
        on.value = true
        app.vela.core.data.NoGoogle.enabled = true
    }
}
