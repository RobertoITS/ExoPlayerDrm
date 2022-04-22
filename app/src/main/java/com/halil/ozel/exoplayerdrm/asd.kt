package com.hvdevs.playmedia.exoplayer2

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.ads.interactivemedia.v3.api.*
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation
import com.google.android.exoplayer2.ext.ima.ImaAdsLoader
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.source.ads.AdPlaybackState
import com.google.android.exoplayer2.source.dash.DashChunkSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.*
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.video.VideoSize
import com.google.common.collect.ImmutableList
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.hvdevs.playmedia.R.*
import com.hvdevs.playmedia.databinding.ActivityPlayerBinding
import com.hvdevs.playmedia.ui.MainListActivity
import com.michael.easydialog.EasyDialog
import kotlin.math.roundToInt
import kotlin.properties.Delegates


class PlayerActivity : Activity(), Player.Listener, AnalyticsListener, AdEvent.AdEventListener,
    AdErrorEvent.AdErrorListener, AdsLoader.AdsLoadedListener {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var binding : ActivityPlayerBinding
    private lateinit var trackSelector: DefaultTrackSelector

    private var adsLoader: ImaAdsLoader? = null
    private var adsManager: AdsManager? = null

    private val formatList: ArrayList<String> = ArrayList()
    private val bitrateList: ArrayList<String> = ArrayList()

    private lateinit var drm: String //Variable que obtiene el drm que se pasa de la actividad anterior
    private lateinit var uri: String //Variable que obtiene la uri que se pasa de la actividad anterior
    private var time by Delegates.notNull<Long>() //Variable que obtiene el tiempo que se pasa de la actividad anterior
    private var testContent by Delegates.notNull<Boolean>() //Variable que obtiene el tipo de contenido que se pasa de la actividad anterior
    private lateinit var uid: String //Variable que obtiene la uid que se pasa de la actividad anterior
    private lateinit var streamUrl: String //Variable para pasar los datos al reproductor
    private lateinit var licenceUrl: String //Variable para pasar los datos al reproductor

    //---------------------------------------------------------------------------------//
    private var playerView: PlayerView? = null
    private var simpleExoPlayer: SimpleExoPlayer? = null
    //---------------------------------------------------------------------------------//

    private lateinit var countDownTimer: CountDownTimer //El contador del reproductor, en caso de modo de prueba
    var total: Long = 0

    private val database = Firebase.database //Instancias a la base de datos
    val myRef = database.getReference("users")

    private val waitUI = Handler() //Instanciamos el handler para el tiempo de espera

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bundle: Bundle? = intent.extras
        uri = bundle?.getString("uri").toString()
        drm = bundle?.getString("licence").toString()
        uid = bundle?.getString("uid").toString()
        time = bundle!!.getLong("time")
        Log.d("TIMETEST", time.toString())
        testContent = bundle.getBoolean("testContent")

        streamUrl = uri
        Log.d("DRM", drm)
        Log.d("URL", uri)
        licenceUrl = drm


        adsLoader = ImaAdsLoader.Builder(this)
            .setAdEventListener(this)
            .setAdErrorListener(this)
            .build()

        binding.detailBtn.setOnClickListener { showDialog() }


        //---------------------------------------------------------------------------------//
        playerView = binding.exoPlayerView

        //Instanciamos las vistas que estan llamadas desde el archivo layout donde se
        //encuentra el exoPlayerView: app:controller_layout_id="@layout/custom_controls"

        //Boton adelantar
        val forwardBtn = playerView!!.findViewById<ImageView>(id.fwd)
        forwardBtn.setOnClickListener {
            simpleExoPlayer!!.seekTo(
                simpleExoPlayer!!.currentPosition + 10000
            )
        }

        //Boton rebobinar
        val rewBtn = playerView!!.findViewById<ImageView>(id.rew)
        rewBtn.setOnClickListener {
            val num = simpleExoPlayer!!.currentPosition - 10000
            if (num < 0) {
                simpleExoPlayer!!.seekTo(0)
            } else {
                simpleExoPlayer!!.seekTo(simpleExoPlayer!!.currentPosition - 10000)
            }
        }

        //Fullscreen
        val fullscreenButton = playerView!!.findViewById<ImageView>(id.fullscreen)
        fullscreenButton.setOnClickListener {
            val orientation: Int =
                this.resources.configuration.orientation
            requestedOrientation = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                // code for portrait mode
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                // code for landscape mode
                Toast.makeText(this, "Land", Toast.LENGTH_SHORT).show()
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }

        //Settings button
        val setting = playerView!!.findViewById<ImageView>(id.exo_track_selection_view)
        setting.setOnClickListener {
            showDialog()
        }

        //Back button
        val back = playerView!!.findViewById<ImageView>(id.exo_back)
        back.setOnClickListener {
            exoPlayer.release()
            finish()
        }
        //---------------------------------------------------------------------------------//

        //Captamos el cambio de visibilidad del UI del sistema
        //Le pasamos un handler para que se oculte a los 3 segundos de espera
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0){
                waitUI.postDelayed(wait, 3000)
            }
        }
    }

    //Creamos la variable de tipo runnable para parar el tiempo,
    //con la funcionalidad a realizarse
    private val wait = Runnable {
        hideSystemUI()
    }

    //Escondemos la barra de estado y de navegacion
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    //Con esta funcion logramos esconder la barra de estado y navegacion
    //Sacado de la pagina oficial de Android Studio
    private fun hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    //Inicializamos es reproductor
    //Las demas funciones viene predeterminadas en el repositorio clonado
    private fun initializePlayer() {
        val userAgent = "ExoPlayer-Drm"
        val drmSchemeUuid = C.WIDEVINE_UUID // DRM Type

        trackSelector = DefaultTrackSelector(this)
        simpleExoPlayer = SimpleExoPlayer.Builder(this).setTrackSelector(trackSelector).build()
        playerView = binding.exoPlayerView
        playerView!!.player = simpleExoPlayer

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
                        .setUri(Uri.parse(uri))
                        // DRM Configuration
                        .setDrmConfiguration(
                            MediaItem.DrmConfiguration.Builder(drmSchemeUuid)
                                .setLicenseUri(licenceUrl).build()
                        )
                        .setMimeType(MimeTypes.APPLICATION_MPD)
                        .setTag(null)
                        .build()
                )

        val handler = Handler()
        val adaptiveTrackSelection = AdaptiveTrackSelection.Factory()
        trackSelector = DefaultTrackSelector(this, adaptiveTrackSelection)
        trackSelector.buildUponParameters().setRendererDisabled(C.TRACK_TYPE_TEXT, true).build()
        val bandwidthMeter = DefaultBandwidthMeter.Builder(this).build()

        bandwidthMeter.addEventListener(handler) { elapsedMs, bytesTransferred, _ ->
            binding.textView.text = (((bytesTransferred * 8).toDouble() / (elapsedMs / 1000)) / 1000).toString()
        }

        exoPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000)
            .build()

        val mEventLogger = EventLogger(trackSelector)
        exoPlayer.addAnalyticsListener(mEventLogger)

        exoPlayer.playWhenReady = true
        binding.exoPlayerView.player = exoPlayer
        exoPlayer.setMediaSource(dashMediaSource, true)
        exoPlayer.prepare()
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

    private fun trackListDetails() {
        formatList.clear()
        bitrateList.clear()

        if (trackSelector.currentMappedTrackInfo != null) {
            val mappedTrackInfo = trackSelector.currentMappedTrackInfo
            for (rendererIndex in 0 until mappedTrackInfo!!.rendererCount) {

                val trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex)

                for (groupIndex in 0 until trackGroupArray.length) {

                    for (trackIndex in 0 until trackGroupArray[groupIndex].length) {
                        val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(resources)
                        val trackName = trackNameProvider.getTrackName(
                            trackGroupArray[groupIndex].getFormat(trackIndex))
                        val widthHeight = trackName.split(",").toTypedArray()
                        val height = widthHeight[0].split("×").toTypedArray()

                        if (rendererIndex == 0) {
                            formatList.add(height[1] + "p")
                            bitrateList.add(widthHeight[1].replace("Mbps","").trim())
                        }
                    }
                }
            }
        }
    }

    override fun onAdEvent(p0: AdEvent?) {
        Log.e("AD_POD_POS", p0?.ad?.adPodInfo?.adPosition.toString())
        Log.e("AD_POD_TOTAL", p0?.ad?.adPodInfo?.totalAds.toString())
        Log.e("AD_POD_INDEX", p0?.ad?.adPodInfo?.podIndex.toString())

    }

    override fun onAdError(p0: AdErrorEvent?) {
        Log.e("AD_ERROR", Gson().toJson(p0))
    }

    override fun onAdsManagerLoaded(p0: AdsManagerLoadedEvent) {
        adsManager = p0.adsManager
        Log.e("CUE_POINTS", adsManager?.adCuePoints.toString())
    }

    override fun onPlaybackStateChanged(playbackState: @Player.State Int) {
        when (playbackState) {
            Player.STATE_IDLE -> {
                println("IDLE")
            }
            Player.STATE_BUFFERING -> {
                println("BUFFERING")
                binding.progressBar.isVisible = true
            }
            Player.STATE_READY -> {
                println("READY")
                binding.progressBar.isGone = true
                //getTrackDetails()
                trackListDetails()
            }
            Player.STATE_ENDED -> {
                println("ENDED")
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e("PLAYBACK_ERROR_MESSAGE", error.toString())
        Log.e("PLAYBACK_ERROR_CAUSE", error.cause.toString())
        Log.e("PLAYBACK_ERROR_CNAME", error.errorCodeName)
        Log.e("PLAYBACK_ERROR_CODE", error.errorCode.toString())
        Log.e("ERROR_STACKTRACE", error.stackTraceToString())
        Log.e("ERROR_SUPP_EXCEPTIONS", error.suppressedExceptions.toString())
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (timeline.isEmpty) {
            return
        }
        val period = timeline.getPeriod(exoPlayer.currentPeriodIndex, Timeline.Period())
        val adGroupTimesUs = LongArray(period.adGroupCount)
        for (i in adGroupTimesUs.indices) {
            adGroupTimesUs[i] = period.getAdGroupTimeUs(i)
        }
        Log.e("AD_GROUPS", Gson().toJson(adGroupTimesUs))
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?) {
        super.onVideoInputFormatChanged(eventTime, format, decoderReuseEvaluation)

        val trackNameProvider: TrackNameProvider = DefaultTrackNameProvider(resources)
        Log.e("VIDEO_BITRATE", trackNameProvider.getTrackName(format))
        Log.e("TRACK_INDEX", format.id.toString())
        println(Gson().toJson(eventTime))
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        Log.e("Video Height: ", videoSize.height.toString())
        Log.e("Video Width: ", videoSize.width.toString())
    }

    private fun releasePlayer() {
        /***/
        exoPlayer.playWhenReady = false
    }

    //Cuando se construye la vista, se iniciliza el exoPlayer
    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23)
        //Pasamos el comprobador de tiempo de prueba, si es true,
        //comienza el contador a ir hacia atras
            if (testContent){
                Log.d("TIMETEST", time.toString())
                countDownTimer = object : CountDownTimer(time, 1000) {
                    @SuppressLint("SetTextI18n")
                    override fun onTick(millisUntilFinished: Long) {
                        //Lo pasamos a formato hora
                        val seconds = (millisUntilFinished / 1000).toInt() % 60
                        val minutes = (millisUntilFinished / (1000 * 60) % 60).toInt()
                        val hours = (millisUntilFinished / (1000 * 60 * 60) % 24).toInt()
                        val newtime = "$hours:$minutes:$seconds"
                        total = millisUntilFinished / 1000
                        Log.d("TIMETEST", millisUntilFinished.toString())
                        binding.testContent.visibility = View.VISIBLE
                        binding.testContent.text = "seconds remaining: $newtime"
                        //Vamos refrescando la hora en la base de datos en tiempo real
                        myRef.child("$uid/time").setValue(millisUntilFinished)
                    }

                    @SuppressLint("SetTextI18n")
                    override fun onFinish() {
                        Toast.makeText(this@PlayerActivity, "Su tiempo de prueba expiro", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }.start()
                initializePlayer()
            } else {
                initializePlayer()
            }
    }

    //Cuando se resume la vista, se reproduce el exoPlayer
    public override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= 23)
            exoPlayer.playWhenReady = true
    }

    //En estado de pausa de la vista, el exoPlayer queda en estado de pausa
    public override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) releasePlayer()
    }

    //En estado de stop de la vista, el exoPlayer queda en estado de pausa
    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) releasePlayer()
    }

    //Cuando se destruye la vista, se destruye el reproductor exoPlayer
    override fun onDestroy() {
        super.onDestroy()
        adsLoader?.release()
        //Se libera el reproductor
        exoPlayer.release()
        Log.e("ADS_DESTROYED", "X")
        if (testContent) countDownTimer.cancel()
    }

    override fun onBackPressed() {
        if (testContent) countDownTimer.cancel()
        super.onBackPressed()
    }
}