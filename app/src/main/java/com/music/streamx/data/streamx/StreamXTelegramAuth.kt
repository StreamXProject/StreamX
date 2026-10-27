package com.music.streamx.data.streamx

import android.content.Context
import android.content.Intent
import com.music.streamx.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.telegram.login.TelegramLogin

/** Captures the chosen server/account for the entire native PKCE login round trip. */
object StreamXTelegramAuth {
    private val LOGIN_HOST = BuildConfig.TELEGRAM_LOGIN_HOST
    data class State(val loading: Boolean = false, val error: String? = null, val completedScope: String? = null)
    private data class Pending(val client: StreamXClient, val redirect: String, val invite: String)
    val state = MutableStateFlow(State())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Pending? = null
    private var job: Job? = null

    fun start(context: Context, inviteCode: String) {
        if (state.value.loading) return
        state.value = State(loading = true)
        job = scope.launch {
            StreamXClient.result {
                check(LOGIN_HOST.isNotBlank()) { "Telegram native login is not configured for this build." }
                val client = StreamXClient.current()
                val clientId = client.telegramClientId()
                check(client.session.scope == StreamXSession.state.value.scope) { "Server changed. Try again." }
                val redirect = "https://$LOGIN_HOST/tglogin"
                pending = Pending(client, redirect, inviteCode)
                TelegramLogin.init(clientId, redirect, listOf("profile"))
                TelegramLogin.startLogin(context)
            }.fold({ state.value = State() }, { pending = null; state.value = State(error = it.message ?: "Could not open Telegram") })
        }
    }

    fun cancel() {
        job?.cancel()
        pending = null
        state.value = State()
    }

    fun consume(context: Context, intent: Intent): Boolean {
        val uri = intent.data ?: return false
        if (uri.scheme != "https" || uri.host != LOGIN_HOST || uri.path != "/tglogin" || uri.port != -1) return false
        val attempt = pending
        if (attempt == null || uri.buildUpon().clearQuery().fragment(null).build().toString() != attempt.redirect ||
            attempt.client.session.scope != StreamXSession.state.value.scope) {
            state.value = State(error = "Telegram sign-in expired. Please start again.")
            return true
        }
        pending = null // Each callback can be consumed once.
        state.value = State(loading = true)
        runCatching {
            TelegramLogin.handleLoginResponse(uri,
                onSuccess = { data ->
                    job = scope.launch {
                        StreamXClient.result { attempt.client.authenticateTelegram(data.idToken, attempt.invite) }
                            .fold({ state.value = State(completedScope = StreamXSession.state.value.scope) },
                                { state.value = State(error = it.message ?: "Telegram sign-in failed") })
                    }
                },
                onError = { state.value = State(error = it.message) },
            )
        }.onFailure { state.value = State(error = "Telegram sign-in expired. Please start again.") }
        return true
    }
}
