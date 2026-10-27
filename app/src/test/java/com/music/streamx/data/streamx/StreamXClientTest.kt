package com.music.streamx.data.streamx

import com.music.streamx.data.model.SearchFilter
import com.music.streamx.data.model.SearchResult
import com.music.streamx.data.sources.SourceConfig
import com.music.streamx.data.sources.SourceKind
import com.music.streamx.data.sources.StreamRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class StreamXClientTest {
    @Test fun playlistCoversSkipUnservedGeneratedPngsButKeepCustomImages() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val endpoint = server.url("/").toString().trimEnd('/')
            val client = StreamXClient(SourceConfig(id = "server", kind = SourceKind.STREAMX, baseUrl = endpoint), StreamXAccountSession())
            server.enqueue(MockResponse().setBody("""{"items":[
                {"playlist_id":"old","name":"Legacy","cover_url":"/covers/user-playlist/old.png","normal_thumbnail":"/covers/user-playlist/old.png","thumbnails":["$endpoint/covers/user-playlist/old.png","https://images.example/real.jpg"]},
                {"playlist_id":"custom","name":"Custom","cover_url":"/images/custom.jpg","thumbnails":["https://images.example/track.jpg"]},
                {"playlist_id":"empty","name":"Empty","cover_url":"/covers/user-playlist/empty.png"}
            ]}"""))
            val playlists = client.playlists()
            assertEquals("https://images.example/real.jpg", playlists[0].thumbnailUrl)
            assertEquals("$endpoint/images/custom.jpg", playlists[1].thumbnailUrl)
            assertNull(playlists[2].thumbnailUrl)
        }
    }

    @Test fun dailyMixesWorkForGuestsAndUseReturnedIds() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = StreamXClient(SourceConfig(id = "server", kind = SourceKind.STREAMX, baseUrl = server.url("/").toString()), StreamXAccountSession())
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"daily:trending","kind":"daily","name":"Trending Today","requires_auth":false},{"id":"private","kind":"daily","name":"Private","requires_auth":true}]}"""))
            val mix = client.dailyMixes(listOf("https://images.example/cover.jpg")).single()
            assertEquals("streamx:server:daily:daily:trending", mix.browseId)
            assertEquals("https://images.example/cover.jpg", mix.thumbnailUrl)
            assertNull(server.takeRequest().getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"one","title":"Daily song"}]}"""))
            client.detail(mix.browseId!!, mix.title, "", null)
            val request = server.takeRequest().requestUrl!!
            assertEquals(listOf("daily-playlist", "daily:trending"), request.pathSegments)
            assertEquals("75", request.queryParameter("limit"))
        }
    }

    @Test fun customCoversAndArtistFollowsUseServerPayloads() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val endpoint = server.url("/").toString()
            val client = StreamXClient(SourceConfig(id = "server", kind = SourceKind.STREAMX, baseUrl = endpoint),
                StreamXAccountSession(endpoint = endpoint, token = "mock-session", userId = 1))
            server.enqueue(MockResponse().setBody("""{"playlist_id":"custom"}"""))
            assertEquals("streamx:server:playlist:custom", client.createPlaylist("My playlist", emptyList(), "https://images.example/custom.jpg"))
            val creation = server.takeRequest().body.readUtf8()
            assertTrue(creation.contains("\"cover_url\":\"https://images.example/custom.jpg\""))
            server.enqueue(MockResponse().setBody("""{"ids":["artist-one"]}"""))
            assertEquals(setOf("artist-one"), client.favouriteArtistIds())
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            client.followArtist("streamx:server:artist:artist-one", true)
            val follow = server.takeRequest()
            assertEquals("POST", follow.method)
            assertEquals("/me/artists/favourites", follow.path)
            assertTrue(follow.body.readUtf8().contains("\"artist_id\":\"artist-one\""))
            server.enqueue(MockResponse().setBody("""{"ok":true}"""))
            client.followArtist("streamx:server:artist:artist-one", false)
            val unfollow = server.takeRequest()
            assertEquals("DELETE", unfollow.method)
            assertEquals("/me/artists/favourites/artist-one", unfollow.path)
        }
    }

    @Test fun cataloguePagesStopAtTotalAndKeepTopicNamesInPath() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = StreamXClient(SourceConfig(id = "server", kind = SourceKind.STREAMX, baseUrl = server.url("/").toString()), StreamXAccountSession())
            val tracks = (1..24).joinToString(",") { """{"id":"$it","title":"Track $it"}""" }
            server.enqueue(MockResponse().setBody("""{"total":25,"items":[$tracks]}"""))
            val first = client.cataloguePage("streamx:server:catalogue:tracks", "Latest songs", 1)
            assertEquals(2, first.nextCataloguePage)
            assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("page"))
            server.enqueue(MockResponse().setBody("""{"total":25,"items":[{"id":"25","title":"Last track"}]}"""))
            assertNull(client.cataloguePage("streamx:server:catalogue:tracks", "Latest songs", 2).nextCataloguePage)
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
            server.enqueue(MockResponse().setBody("""{"total":1,"items":[{"id":"topic-track","title":"Topic song"}]}"""))
            client.cataloguePage("streamx:server:catalogue:topic/topic_2", "topic_2", 1)
            val topicRequest = server.takeRequest().requestUrl!!
            assertEquals("/topics/topic_2/tracks", topicRequest.encodedPath)
            assertEquals("24", topicRequest.queryParameter("limit"))
            server.enqueue(MockResponse().setBody("""{"total":1,"items":[{"id":"artist-id","name":"Artist"}]}"""))
            val artists = client.cataloguePage("streamx:server:catalogue:artists", "Artists", 1)
            assertEquals("streamx:server:artist:artist-id", artists.sections.single().items.single().browseId)
            assertNull(artists.nextCataloguePage)
        }
    }

    @Test fun readsPlaylistArtworkAndFallsBackToTrackCover() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val endpoint = server.url("/").toString().trimEnd('/')
            val client = StreamXClient(SourceConfig(id = "server", kind = SourceKind.STREAMX, baseUrl = endpoint), StreamXAccountSession())
            server.enqueue(MockResponse().setBody("""{"items":[{"playlist_id":"one","name":"First","normal_thumbnail":"/normal.jpg","thumbnails":["/ignored.jpg"]},{"playlist_id":"two","name":"Second","thumbnails":["","/track.jpg"]}]}"""))
            assertEquals(listOf("$endpoint/normal.jpg", "$endpoint/track.jpg"), client.playlists().map { it.thumbnailUrl })
            server.enqueue(MockResponse().setBody("""{"total":1,"items":[{"id":"track","title":"Song","cover_url":"/fallback.jpg"}]}"""))
            assertEquals("$endpoint/fallback.jpg", client.detail("streamx:server:playlist:one", "First", "", null).thumbnailUrl)
        }
    }

    @Test fun searchEncodesQueryAndMapsServerTrack() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val endpoint = server.url("/").toString().trimEnd('/')
            val client = StreamXClient(SourceConfig(id = "server", kind = SourceKind.STREAMX, baseUrl = endpoint),
                StreamXAccountSession(endpoint = endpoint, token = "test-token"))
            server.enqueue(MockResponse().setBody("""{"items":[{"id":"track/1","title":"Song","artist":"Artist","duration_sec":125,"type":"flac","cover_url":"/cover.jpg"}]}"""))
            val song = (client.search("A & B?", SearchFilter.SONGS).single() as SearchResult.Track).song
            val request = server.takeRequest()
            assertEquals("A & B?", request.requestUrl!!.queryParameter("q"))
            assertEquals("Bearer test-token", request.getHeader("Authorization"))
            assertEquals("src:server::track/1", song.videoId)
            assertEquals("2:05", song.durationText)
            assertEquals("$endpoint/cover.jpg", song.thumbnailUrl)
        }
    }

    @Test fun readsWrappedFavouritesAndSkipsDeletedTracks() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = StreamXClient(SourceConfig(id = "server", kind = SourceKind.STREAMX, baseUrl = server.url("/").toString()), StreamXAccountSession())
            server.enqueue(MockResponse().setBody("""{"total":2,"items":[{"track":{"id":"liked","title":"Favourite","artist":"A","type":"alac"},"created_at":123},{"track":null}]}"""))
            val songs = client.allSongs(listOf("me", "favourites"))
            assertEquals("src:server::liked", songs.single().videoId)
            assertEquals("LOSSLESS", songs.single().sourceQuality)
        }
    }

    @Test fun telegramUsesSelectedServerAndSendsIdTokenForVerification() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = StreamXClient(SourceConfig(id = "server", kind = SourceKind.STREAMX, baseUrl = server.url("/").toString()), StreamXAccountSession())
            server.enqueue(MockResponse().setBody("""{"enabled":true,"client_id":"12345"}"""))
            assertEquals("12345", client.telegramClientId())
            assertEquals("/auth/telegram/config", server.takeRequest().path)
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"invite required"}"""))
            assertTrue(StreamXClient.result { client.authenticateTelegram("telegram-id-token", "invite") }.isFailure)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/auth/telegram/validate-token?set_cookie=false", request.path)
            val body = kotlinx.serialization.json.Json.parseToJsonElement(request.body.readUtf8()).toString()
            assertTrue(body.contains("\"id_token\":\"telegram-id-token\""))
            assertTrue(body.contains("\"invite_code\":\"invite\""))
        }
    }

    @Test fun credentialsNeverFollowAnEndpointChange() {
        val client = StreamXClient(SourceConfig(kind = SourceKind.STREAMX, baseUrl = "https://new.example"),
            StreamXAccountSession(endpoint = "https://old.example", token = "private"))
        assertTrue(client.headers().isEmpty())
    }

    @Test fun followsCataloguePagesAndMapsNestedAudio() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = StreamXClient(SourceConfig(id = "server", kind = SourceKind.STREAMX, baseUrl = server.url("/").toString()), StreamXAccountSession())
            server.enqueue(MockResponse().setBody("""{"total":2,"items":[{"_id":"one","audio":{"title":"First","artist":"A","duration_sec":10}}]}"""))
            server.enqueue(MockResponse().setBody("""{"total":2,"items":[{"_id":"two","audio":{"title":"Second","artist":"B","duration_sec":20}}]}"""))
            assertEquals(listOf("First", "Second"), client.allSongs(listOf("tracks")).map { it.title })
            assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("page"))
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
        }
    }

    @Test fun originalLosslessAudioRespectsDataCap() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val client = StreamXClient(SourceConfig(kind = SourceKind.STREAMX, baseUrl = server.url("/").toString()), StreamXAccountSession())
            server.enqueue(MockResponse().setBody("""{"_id":"one","audio":{"type":"flac","bitrate_kbps":900}}"""))
            assertNull(client.stream("one", StreamRequest.Capped(128)))
        }
    }
}
