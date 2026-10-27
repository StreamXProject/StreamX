package com.music.streamx.ui.preview

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.music.streamx.data.streamx.StreamXSession
import com.music.streamx.data.streamx.StreamXServer
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.music.streamx.ui.screens.StartModeScreen
import com.music.streamx.ui.screens.StreamXSetupPage
import com.music.streamx.ui.theme.StreamXTheme

@Composable
private fun SetupPreview(page: StreamXSetupPage, darkTheme: Boolean = true) {
    StreamXTheme(darkTheme = darkTheme) {
        StartModeScreen(
            initialEndpoint = if (page == StreamXSetupPage.ENDPOINT) "" else "https://music.example.com",
            onEndpointAdded = {},
            onAuthenticated = {},
            reduceAnimation = true,
            initialPage = page,
        )
    }
}

@Preview(name = "StreamGO · Start screen", widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
private fun StreamXStartPreview() = SetupPreview(StreamXSetupPage.START)

@Preview(name = "StreamGO · Add server", widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
private fun StreamXEndpointPreview() = SetupPreview(StreamXSetupPage.ENDPOINT)

@Preview(name = "StreamGO · Choose access", widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
private fun StreamXAuthPreview() = SetupPreview(StreamXSetupPage.AUTH)

@Preview(name = "StreamGO · Server password", widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
private fun StreamXServerPasswordPreview() = SetupPreview(StreamXSetupPage.SERVER_PASSWORD)

@Preview(name = "StreamGO · User login", widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
private fun StreamXLoginPreview() = SetupPreview(StreamXSetupPage.USER_LOGIN)

@Preview(name = "StreamGO · Sign up · Small screen", widthDp = 320, heightDp = 568, showSystemUi = true)
@Composable
private fun StreamXSignupPreview() = SetupPreview(StreamXSetupPage.SIGN_UP)

@Preview(name = "StreamGO · Light theme", widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
private fun StreamXLightPreview() = SetupPreview(StreamXSetupPage.ENDPOINT, darkTheme = false)

@Preview(name = "StreamGO · Login · Light", widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
private fun StreamXLoginLightPreview() = SetupPreview(StreamXSetupPage.USER_LOGIN, darkTheme = false)

@Preview(name = "StreamGO · StreamX settings", widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
private fun StreamXSettingsPreview() {
    StreamXTheme(darkTheme = true) {
        androidx.compose.material3.Surface {
        com.music.streamx.ui.screens.StreamXSettingsContent(
            session = com.music.streamx.data.streamx.StreamXAccountSession(
                endpoint = "https://music.example.com", token = "preview", userId = 1L, name = "Misfit"),
            onOpenAccount = {}, servers = listOf(
                StreamXServer("home", "https://music.example.com", "Home library"),
                StreamXServer("studio", "https://studio.example.com", "Studio")), onSelectServer = {}, onAddServer = {}, onSignOut = {},
        )
        }
    }
}
