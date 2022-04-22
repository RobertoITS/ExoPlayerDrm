package com.halil.ozel.exoplayerdrm

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.dash.DashChunkSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.MimeTypes
import com.halil.ozel.exoplayerdrm.databinding.ActivityMainBinding

/** DRM URL : https://bitmovin-a.akamaihd.net/content/art-of-motion_drm/mpds/11331.mpd **/
/** NON DRM URL : https://bitmovin-a.akamaihd.net/content/MI201109210084_1/mpds/f08e80da-bf1d-4e3d-8899-f0f6155f6efa.mpd **/

class MainActivity : Activity() {

    private lateinit var playerView: ExoPlayer
    private lateinit var binding: ActivityMainBinding
    private lateinit var trackSelector: DefaultTrackSelector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        initializePlayer()
    }


    private fun initializePlayer() {

        val url = "https://chromecast.cvattv.com.ar/live/c3eds/AmericaTV/SA_Live_dash_enc_2A/AmericaTV.mpd"
        val drmLicenseUrl = "https://wv-client.cvattv.com.ar/?deviceId=Y2MzZWViN2QwNDZjNjZkZTQyNmE4NmE1ZGMxY2JmNWY="
        val drmSchemeUuid = C.WIDEVINE_UUID // DRM Type
        val userAgent = "ExoPlayer-Drm"
        // val userAgent = "userAgent"

        trackSelector = DefaultTrackSelector(this)
        var exoPlayer = ExoPlayer.Builder(this).setTrackSelector(trackSelector).build()

        val handler = Handler()
        val adaptiveTrackSelection = AdaptiveTrackSelection.Factory()
        trackSelector = DefaultTrackSelector(this, adaptiveTrackSelection)
        trackSelector.buildUponParameters().setRendererDisabled(C.TRACK_TYPE_TEXT, true).build()

        val bandwidthMeter = DefaultBandwidthMeter.Builder(this).build()

        bandwidthMeter.addEventListener(handler) { elapsedMs, bytesTransferred, _ ->
//            binding.textView.text = (((bytesTransferred * 8).toDouble() / (elapsedMs / 1000)) / 1000).toString()
        }

        val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setTransferListener(
                DefaultBandwidthMeter.Builder(this)
                    .setResetOnNetworkTypeChange(false)
                    .build()
            )

        val dashChunkSourceFactory: DashChunkSource.Factory = DefaultDashChunkSource.Factory(
            defaultHttpDataSourceFactory
        )
        val manifestDataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent(userAgent)
        val dashMediaSource =
            DashMediaSource.Factory(dashChunkSourceFactory, manifestDataSourceFactory)
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(Uri.parse(url))
                        // DRM Configuration
                        .setDrmConfiguration(
                            MediaItem.DrmConfiguration.Builder(drmSchemeUuid)
                                .setLicenseUri(drmLicenseUrl).build()
                        )
                        .setMimeType(MimeTypes.APPLICATION_MPD)
                        .setTag(null)
                        .build()
                )


        // Prepare the player.
        playerView = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build()
        playerView.playWhenReady = true
        binding.playerView.player = playerView
        playerView.setMediaSource(dashMediaSource, true)
        playerView.prepare()
    }

    private fun showDialog() {
        val trackSelector = TrackSelectionDialogBuilder(
            this,
            "Select Track",
            trackSelector,
            0
        ).build()
        trackSelector.show()
    }

    override fun onPause() {
        super.onPause()
        playerView.playWhenReady = false
    }
}