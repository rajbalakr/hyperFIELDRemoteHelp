package com.hyperfield.remotehelp

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentTransaction
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.RecyclerView
import com.hyperfield.remotehelp.AppConfig.otConfig
import com.hyperfield.remotehelp.extension.getBitmap
import com.hyperfield.remotehelp.extension.hide
import com.hyperfield.remotehelp.extension.merge
import com.hyperfield.remotehelp.extension.show
import com.hyperfield.remotehelp.toyvpn.ToyVpnService
import com.hyperfield.remotehelp.ui.ActionBarFragment
import com.hyperfield.remotehelp.ui.ActionBarFragment.PreviewControlCallbacks
import com.hyperfield.remotehelp.ui.Participant
import com.hyperfield.remotehelp.ui.ParticipantsAdapter
import com.hyperfield.remotehelp.ui.ParticipantsAdapter.ParticipantAdapterListener
import com.hyperfield.remotehelp.ui.ScreenSharingBar
import com.hyperfield.remotehelp.util.FileUtil
import com.opentok.accelerator.annotation.AnnotationsToolbar
import com.opentok.accelerator.annotation.AnnotationsView
import com.opentok.accelerator.annotation.AnnotationsView.AnnotationsListener
import com.opentok.accelerator.annotation.utils.AnnotationsVideoRenderer
import com.opentok.accelerator.core.listeners.AdvancedListener
import com.opentok.accelerator.core.listeners.BasicListener
import com.opentok.accelerator.core.listeners.PausableAdvancedListener
import com.opentok.accelerator.core.listeners.PausableBasicListener
import com.opentok.accelerator.core.utils.MediaType
import com.opentok.accelerator.core.utils.PreviewConfig.PreviewConfigBuilder
import com.opentok.accelerator.core.utils.StreamStatus
import com.opentok.accelerator.core.wrapper.OTWrapper
import com.opentok.accelerator.textchat.ChatMessage
import com.opentok.accelerator.textchat.TextChatFragment
import com.opentok.accelerator.textchat.TextChatFragment.TextChatListener
import com.opentok.android.OpentokError
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*


// Remove these listeners and use Kotlin functions
class MainActivity : AppCompatActivity(), PreviewControlCallbacks, AnnotationsListener,
    TextChatListener, ParticipantAdapterListener {

    companion object {
        private val LOG_TAG = MainActivity::class.java.simpleName

        private const val PERMISSION_CODE = 200

        private val PERMISSIONS = arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.USE_SIP,
                Manifest.permission.READ_PHONE_STATE,
        )
    }

    //OpenTok calls
    lateinit var otWrapper: OTWrapper
        private set

    //Participants Grid management
    private lateinit var participantsGrid: RecyclerView
    private lateinit var gridLayoutManager: GridLayoutManager
    private lateinit var participantsAdapter: ParticipantsAdapter
    private val participantsList = mutableListOf<Participant>()

    //Fragments and containers
    private lateinit var actionBarFragment: ActionBarFragment
    private lateinit var fragmentTransaction: FragmentTransaction
    private lateinit var textChatContainer: FrameLayout
    private lateinit var actionBarContainer: RelativeLayout
    private lateinit var textChatFragment: TextChatFragment

    //Annotations
    private lateinit var annotationsToolbar: AnnotationsToolbar
    private lateinit var remoteRenderer: AnnotationsVideoRenderer
    private lateinit var screenSharingRenderer: AnnotationsVideoRenderer
    private var remoteAnnotationsView: AnnotationsView? = null

    //ScreenSharing
    private lateinit var screenSharingContainer: RelativeLayout
    private var screenAnnotationsView: AnnotationsView? = null
    private lateinit var screenSharingView: ViewGroup
    private lateinit var callToolbar: TextView
    private lateinit var webViewContainer: WebView
    private lateinit var alert: TextView
    private lateinit var progressBar: ProgressBar
    private var screenSharingBar: ScreenSharingBar? = null
    private var screenRemoteId: String? = null
    private var countDownTimer: CountDownTimer? = null

    private var audioPermission = false
    private var videoPermission = false
    private var writeExternalStoragePermission = false
    private var readExternalStoragePermission = false

    private var isConnected = false

    var isCallInProgress = false
        private set

    private var isRemoteAnnotations = false

    var isScreenSharing = false
        private set

    private var isAnnotations = false
    private var isReadyToCall = false

    //Current orientation
    private var orientation = 0

    //Current remote
    private var mCurrentRemote: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_ACTION_BAR)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        participantsGrid = findViewById(R.id.gridContainer)
        gridLayoutManager = GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false)
        setupMultipartyLayout()
        participantsGrid.layoutManager = gridLayoutManager
        try {
            participantsAdapter = ParticipantsAdapter(this, participantsList, this)
            participantsGrid.adapter = participantsAdapter
        } catch (e: Exception) {
            e.printStackTrace()
        }
        webViewContainer = findViewById(R.id.webView)
        alert = findViewById(R.id.qualityWarning)
        screenSharingContainer = findViewById(R.id.screenSharingContainer)
        actionBarContainer = findViewById(R.id.actionbar_fragment_container)
        textChatContainer = findViewById(R.id.textChatFragmentContainer)
        annotationsToolbar = findViewById(R.id.annotationsBar)
        callToolbar = findViewById(R.id.callToolbar)
        progressBar = findViewById(R.id.progressBar)

        findViewById<TextView>(R.id.callToolbar).setOnClickListener {
            showAll()
        }

        //request runtime camera permission
        if (ContextCompat.checkSelfPermission(
                        this,
                        PERMISSIONS[1]
                ) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                        this,
                        PERMISSIONS[0]
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(PERMISSIONS, PERMISSION_CODE)
            }
        } else {
            videoPermission = true
            audioPermission = true
            writeExternalStoragePermission = true
            readExternalStoragePermission = true
        }
        otWrapper = OTWrapper(this@MainActivity, otConfig)

        //set listener to receive the communication events, and add UI to these events
        otWrapper.addBasicListener(basicListener)
        otWrapper.addAdvancedListener(advancedListener)
        //use a custom video renderer for the annotations. It will be applied to the remote. It will be applied before to start subscribing
        remoteRenderer = AnnotationsVideoRenderer(this)
        screenSharingRenderer = AnnotationsVideoRenderer(this)
        otWrapper.setRemoteVideoRenderer(remoteRenderer, true)

        //connect
        otWrapper.connect()
        progressBar.show()

        //init controls fragments
        if (savedInstanceState == null) {
            fragmentTransaction = supportFragmentManager.beginTransaction()
            initActionBarFragment() //to enable/disable local media
            initTextChatFragment() //to send/receive text-messages
            fragmentTransaction.commitAllowingStateLoss()
        }

        //get orientation
        orientation = resources.configuration.orientation
    }

    override fun onPause() {
        super.onPause()
        otWrapper.pause()
    }

    override fun onResume() {
        super.onResume()
        otWrapper.resume(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.activity_menu, menu)
        return true
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>,
            grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_CODE) {
            videoPermission = grantResults[0] == PackageManager.PERMISSION_GRANTED
            audioPermission = grantResults[1] == PackageManager.PERMISSION_GRANTED
            readExternalStoragePermission = grantResults[2] == PackageManager.PERMISSION_GRANTED
            writeExternalStoragePermission = grantResults[3] == PackageManager.PERMISSION_GRANTED
            if (!videoPermission || !audioPermission || !readExternalStoragePermission || !writeExternalStoragePermission) {
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setTitle(resources.getString(R.string.permissions_denied_title))
                builder.setMessage(resources.getString(R.string.alert_permissions_denied))
                builder.setPositiveButton("I'M SURE") { dialog, _ -> dialog.dismiss() }
                builder.setNegativeButton("RE-TRY") { dialog, _ ->
                    dialog.dismiss()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        requestPermissions(permissions, PERMISSION_CODE)
                    }
                }
                builder.show()
            }
        }
    }

    //ParticipantAdapter listener
    override fun mediaControlChanged(remoteId: String?) {
        Log.i(LOG_TAG, "Participant control changed: $remoteId")
        mCurrentRemote = remoteId
    }

    override fun onScreenSharing() {
        if (isScreenSharing) {
            stopScreenSharing()
            isCallInProgress = true
            showAudioVideoCall(true)
            otWrapper.startPublishingMedia(PreviewConfigBuilder().name("hyperFIELD").build(), false) //restart av call
            webViewContainer.hide()
            actionBarFragment.showAnnotations(false)
        } else {
            isScreenSharing = true
            showAudioVideoCall(false)
            otWrapper.stopPublishingMedia(false) //stop call
            isCallInProgress = false
            val builder = PreviewConfigBuilder().name("hyperFIELDScreen").renderer(screenSharingRenderer)
            otWrapper.startPublishingMedia(builder.build(), true) //start screen sharing
            webViewContainer.show()
            actionBarFragment.showAnnotations(true)
        }
    }

    override fun onAnnotations() {
        if (!isAnnotations) {
            showAnnotationsToolbar(true)
        } else {
            showAnnotationsToolbar(false)
        }
    }

    override fun onVPN() {
        Log.w(LOG_TAG, "Starting VPN")
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            onActivityResult(0, RESULT_OK, null)
        }
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        if (result == RESULT_OK) {
            startService(getServiceIntent()?.setAction(ToyVpnService.ACTION_CONNECT))
        }
        super.onActivityResult(request, result, data);
    }

    private fun getServiceIntent(): Intent? {
        return Intent(this, ToyVpnService::class.java)
    }

    override fun onTextChat() {
        if (textChatContainer.visibility == View.VISIBLE) {
            textChatContainer.hide()
            showAudioVideoCall(true)
        } else {
            showAudioVideoCall(false)
            textChatContainer.show()
            actionBarFragment.unreadMessages(false)
        }
    }

    //TextChat Fragment listener events
    override fun onNewSentMessage(message: ChatMessage) {
        Log.i(LOG_TAG, "New sent message")
    }

    override fun onNewReceivedMessage(message: ChatMessage) {
        Log.i(LOG_TAG, "New received message")
        if (textChatContainer.visibility != View.VISIBLE) {
            actionBarFragment.unreadMessages(true)
        } else actionBarFragment.unreadMessages(false)
    }

    override fun onTextChatError(error: String) {
        Log.i(LOG_TAG, "Error on text chat $error")
    }

    override fun onClosed() {
        Log.i(LOG_TAG, "OnClosed text-chat")
        textChatContainer.hide()
        showAudioVideoCall(true)
        actionBarFragment.unreadMessages(false)
        restartTextChatLayout(true)
    }

    override fun onRestarted() {
        Log.i(LOG_TAG, "Restarted text chat")
    }

    //Video local button event
    override fun onDisableLocalVideo(video: Boolean) {
        Log.i(LOG_TAG, "Disable/Enable local video")
        otWrapper.enableLocalMedia(MediaType.VIDEO, video)
        updateParticipant(Participant.Type.LOCAL, null, video)
    }

    override fun onDisableLocalAudio(audio: Boolean) {
        Log.i(LOG_TAG, "Disable/Enable local audio_icon")
        otWrapper.enableLocalMedia(MediaType.AUDIO, audio)
    }

    //Call button event
    override fun onCall() {
        Log.i(LOG_TAG, "OnCall")
        actionBarFragment.setCallButtonEnabled(false)
        if (isConnected) {
            if (!isCallInProgress && !isScreenSharing) {
                isCallInProgress = true
                otWrapper.startPublishingMedia(PreviewConfigBuilder().name("hyperFIELD").build(), false)
                actionBarFragment.setEnabled(true)
            } else {
                if (isScreenSharing) {
                    stopScreenSharing()
                    showAudioVideoCall(true)
                } else {
                    otWrapper.stopPublishingMedia(false)
                    otWrapper.disconnect()
                    isCallInProgress = false
                }
                cleanViewsAndControls()
            }
        } else {
            isReadyToCall = true
            otWrapper.connect()
        }
    }

    //Annotations events
    override fun onScreencaptureReady(bmp: Bitmap) {
        Log.i(LOG_TAG, "Screen capture is ready")
        saveScreenCapture(bmp)
    }

    override fun onAnnotationsSelected(mode: AnnotationsView.Mode) {
        if (mode == AnnotationsView.Mode.Pen || mode == AnnotationsView.Mode.Text) {
            showAll()
            //show minimized call toolbar
            callToolbar.show()
            val params = annotationsToolbar.layoutParams as RelativeLayout.LayoutParams
            params.addRule(RelativeLayout.ABOVE, callToolbar.id)
            annotationsToolbar.layoutParams = params
            annotationsToolbar.show()
        }
    }

    override fun onAnnotationsDone() {
        Log.i(LOG_TAG, "Annotations have been done")
    }

    override fun onError(error: String) {
        Log.i(LOG_TAG, "Annotations error: $error")
    }

    //Basic Listener from OTWrapper
    private val basicListener = PausableBasicListener<OTWrapper>(object : BasicListener<OTWrapper> {
        override fun onConnected(otWrapper: OTWrapper, participantsCount: Int, connId: String, data: String) {
            Log.i(LOG_TAG, "Connected to the session. Number of participants: $participantsCount, connId: $connId")
            if (this@MainActivity.otWrapper.ownConnId == connId) {
                isConnected = true
                progressBar.hide()
                //ToDO: github ticket
                //TextChatFragment requires a session. In the current accelerator, the session is connected in the
                // app and then,
                // the accelerator is initialized.
                textChatFragment.init()
            }
            if (isReadyToCall) {
                isReadyToCall = false
                onCall()
            }
        }

        override fun onDisconnected(otWrapper: OTWrapper, participantsCount: Int, connId: String, data: String) {
            Log.i(
                    LOG_TAG,
                    "Connection dropped: Number of participants: $participantsCount, connId: $connId"
            )
            if (connId == this@MainActivity.otWrapper.ownConnId) {
                Log.i(LOG_TAG, "Disconnected to the session")
                cleanViewsAndControls()
                isConnected = false
            }
        }

        override fun onPreviewViewReady(otWrapper: OTWrapper, localView: View) {
            Log.i(LOG_TAG, "Local preview view is ready")

            if (isScreenSharing) {
                //Share local web view
                screenSharingView = localView as ViewGroup //ToDo: Is this always ViewGroup? Update listener definition
                webViewContainer.webViewClient = WebViewClient()
                val webSettings = webViewContainer.settings
                webSettings.javaScriptEnabled = true
                webViewContainer.loadUrl("https://www.hyperfield.com")
            } else {
                //audio/video call view
                val participant = Participant(
                        Participant.Type.LOCAL,
                        this@MainActivity.otWrapper.localStreamStatus,
                        participantSize
                )
                addNewParticipant(participant)
            }
        }

        override fun onPreviewViewDestroyed(otWrapper: OTWrapper) {
            Log.i(LOG_TAG, "Local preview view is destroyed")
            participantsList.removeAll { it.type == Participant.Type.LOCAL }
            updateParticipantList()
        }

        override fun onRemoteViewReady(otWrapper: OTWrapper, remoteView: View, remoteId: String, data: String) {
            Log.i(LOG_TAG, "Participant remote view is ready")

            if (this@MainActivity.otWrapper.getRemoteStreamStatus(remoteId).type == StreamStatus.StreamType.SCREEN) {
                Log.i(LOG_TAG, "Participant is sharing the screen")
                screenRemoteId = remoteId
                addRemoteScreenSharing(remoteId, remoteView)
            } else {
                val newParticipant = Participant(
                        Participant.Type.REMOTE,
                        this@MainActivity.otWrapper.getRemoteStreamStatus(remoteId),
                        participantSize,
                        remoteId
                )
                addNewParticipant(newParticipant)
            }
        }

        override fun onRemoteViewDestroyed(otWrapper: OTWrapper, remoteId: String) {
            Log.i(LOG_TAG, "Remote view is destroyed")

            if (remoteId == screenRemoteId) {
                screenRemoteId = null
                removeRemoteScreenSharing()
            } else {
                participantsList.removeAll { it.type == Participant.Type.REMOTE && it.remoteId == remoteId }
                updateParticipantList()
            }
        }

        override fun onStartedPublishingMedia(otWrapper: OTWrapper, screenSharing: Boolean) {
            Log.i(LOG_TAG, "Local started streaming video.")

            if (screenSharing) {
                screenAnnotations()
            }
            actionBarFragment.setCallButtonEnabled(true)
        }

        override fun onStoppedPublishingMedia(otWrapper: OTWrapper, screenSharing: Boolean) {
            Log.i(LOG_TAG, "Local stopped streaming video.")

            actionBarFragment.setCallButtonEnabled(true)
            isCallInProgress = false
        }

        override fun onRemoteJoined(otWrapper: OTWrapper, remoteId: String) {
            Log.i(LOG_TAG, "A new remote joined.")
        }

        override fun onRemoteLeft(otWrapper: OTWrapper, remoteId: String) {
            Log.i(LOG_TAG, "A new remote left.")
        }

        override fun onRemoteVideoChanged(
                otWrapper: OTWrapper?,
                remoteId: String,
                reason: String,
                videoActive: Boolean,
                subscribed: Boolean
        ) {
            Log.i(LOG_TAG, "Remote video changed")

            if (isCallInProgress) {
                if (reason == "quality") {
                    //network quality alert
                    alert.setBackgroundResource(R.color.quality_alert)
                    alert.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                    alert.bringToFront()
                    alert.show()
                    alert.postDelayed({ alert.hide() }, 7000)
                }
                updateParticipant(Participant.Type.REMOTE, remoteId, videoActive)
            }
        }

        override fun onError(otWrapper: OTWrapper, error: OpentokError) {
            Log.i(LOG_TAG, "Error " + error.errorCode + "-" + error.message)
            Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
            this@MainActivity.otWrapper.disconnect()
            progressBar.hide()
            cleanViewsAndControls() //restart views
            actionBarFragment.setCallButtonEnabled(false)
        }
    })

    //Advanced Listener from OTWrapper
    private val advancedListener = PausableAdvancedListener<OTWrapper>(object : AdvancedListener<OTWrapper> {
        override fun onCameraChanged(otWrapper: OTWrapper) {
            Log.i(LOG_TAG, "The camera changed")
        }

        override fun onReconnecting(otWrapper: OTWrapper) {
            Log.i(LOG_TAG, "The session is reconnecting.")
            Toast.makeText(this@MainActivity, R.string.reconnecting, Toast.LENGTH_LONG).show()
        }

        override fun onReconnected(otWrapper: OTWrapper) {
            Log.i(LOG_TAG, "The session reconnected.")
            Toast.makeText(this@MainActivity, R.string.reconnected, Toast.LENGTH_LONG).show()
        }

        override fun onReconnected(otWrapper: OTWrapper, remoteId: String) {
            Log.i(LOG_TAG, "The session reconnected remoteId: $remoteId")
            Toast.makeText(this@MainActivity, R.string.reconnected, Toast.LENGTH_LONG).show()
        }

        override fun onDisconnected(otWrapper: OTWrapper, remoteId: String) {
            Log.i(LOG_TAG, "The session disconnected remoteId: $remoteId")
            Toast.makeText(this@MainActivity, R.string.disconnected, Toast.LENGTH_LONG).show()
        }

        override fun onAudioEnabled(otWrapper: OTWrapper, remoteId: String) {
            Log.i(LOG_TAG, "Audio enabled remoteId: $remoteId")
            Toast.makeText(this@MainActivity, R.string.audio_enabled, Toast.LENGTH_LONG).show()
        }

        override fun onAudioDisabled(otWrapper: OTWrapper, remoteId: String) {
            Log.i(LOG_TAG, "Audio disabled remoteId: $remoteId")
            Toast.makeText(this@MainActivity, R.string.audio_disabled, Toast.LENGTH_LONG).show()
        }

        override fun onVideoQualityWarning(otWrapper: OTWrapper, remoteId: String) {
            Log.i(LOG_TAG, "The quality has degraded")
            alert.setBackgroundResource(R.color.quality_warning)
            alert.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.warning_text))
            alert.bringToFront()
            alert.show()
            alert.postDelayed({ alert.hide() }, 7000)
        }

        override fun onVideoQualityWarningLifted(otWrapper: OTWrapper, remoteId: String) {
            Log.i(LOG_TAG, "The quality has improved")
        }

        override fun onAudioLevelUpdated(audioLevel: Float) {
            Log.i(LOG_TAG, "Audio level changed. Level: $audioLevel")
        }

        override fun onError(otWrapper: OTWrapper, error: OpentokError) {
            Log.i(LOG_TAG, "Error " + error.errorCode + "-" + error.message)
            Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
            this@MainActivity.otWrapper.disconnect() //end communication
            progressBar.hide()
            cleanViewsAndControls() //restart views
        }
    })

    //Private methods
    private fun stopScreenSharing() {
        //hide screen sharing bar and view
        isScreenSharing = false
        screenSharingView.removeView(screenAnnotationsView)
        showScreenSharingBar(false)
        actionBarFragment.restartScreenSharing() //restart screen sharing UI

        //hide annotations
        showAnnotationsToolbar(false)
        otWrapper.stopPublishingMedia(true)
    }

    private fun saveScreenCapture(bitmap: Bitmap?) {
        bitmap ?: return

        val annotationsBmp = remoteAnnotationsView?.getBitmap()

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val filename = "$timeStamp.jpg"
            val path = getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString()
            val outputStream: OutputStream?
            val file = File(path, filename)

            outputStream = FileOutputStream(file)
            val overlayBitmap = bitmap.merge(annotationsBmp)
            overlayBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()

            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, timeStamp)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, file.absolutePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            FileUtil.shareImage(this, file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAnnotationsToolbar(show: Boolean) {
        isAnnotations = show
        if (show) {
            val params = annotationsToolbar.layoutParams as RelativeLayout.LayoutParams
            params.addRule(RelativeLayout.ABOVE, actionBarContainer.id)
            annotationsToolbar.layoutParams = params
            annotationsToolbar.show()
            actionBarContainer.show()
        } else {

            if (countDownTimer != null) {
                countDownTimer?.cancel()
                countDownTimer = null
            }
            callToolbar.hide()
            annotationsToolbar.hide()
            actionBarContainer.show()
            annotationsToolbar.restart()
        }
    }

    private fun showAll() {
        callToolbar.hide()
        showAnnotationsToolbar(true)
        countDownTimer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (isAnnotations) {
                    callToolbar.show()
                    val params = annotationsToolbar.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ABOVE, callToolbar.id)
                    annotationsToolbar.layoutParams = params
                    annotationsToolbar.show()
                    actionBarContainer.hide()
                }
            }
        }.start()
    }

    private fun showScreenSharingBar(show: Boolean) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (show) {
            screenSharingBar = ScreenSharingBar(this)
            screenSharingBar?.setOnCloseListener {
                onScreenSharing()
            }

            //add screen sharing bar on top of the screen
            val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.LEFT or Gravity.TOP
            params.x = 0
            params.y = 0
            wm.addView(screenSharingBar, params)
        } else {
            wm.removeView(screenSharingBar)
            screenSharingBar = null
        }
    }

    private fun initActionBarFragment() {
        actionBarFragment = ActionBarFragment()
        supportFragmentManager.beginTransaction()
            .add(R.id.actionbar_fragment_container, actionBarFragment).commit()
        if (isRemoteAnnotations || isAnnotations) {
            actionBarFragment.enableAnnotations(true)
        }
    }

    private fun initTextChatFragment() {
        textChatFragment = TextChatFragment.newInstance(otWrapper.session, otConfig.apiKey)
        supportFragmentManager.beginTransaction()
            .add(R.id.textChatFragmentContainer, textChatFragment).commit()
        supportFragmentManager.executePendingTransactions()
        try {
            textChatFragment.senderAlias = "Vonage"
            textChatFragment.maxTextLength = 140
        } catch (e: Exception) {
            e.printStackTrace()
        }
        textChatFragment.setListener(this)
    }

    private fun cleanViewsAndControls() {
        participantsGrid.removeAllViews()
        participantsList.clear()
        updateParticipantList()
        actionBarFragment.restart()
        restartTextChatLayout(true)
        textChatFragment.restart()
        textChatContainer.hide()
        actionBarContainer.background = null
        webViewContainer.hide()
        remoteAnnotationsView = null
        mCurrentRemote = null
        screenSharingContainer.hide()
        screenAnnotationsView?.removeAllViews()
        isCallInProgress = false
        isReadyToCall = false
        isRemoteAnnotations = false
        isScreenSharing = false
        isAnnotations = false
        restartOrientation()
    }

    private fun showAudioVideoCall(show: Boolean) {
        if (show && screenRemoteId == null) {
            participantsGrid.show()
            screenSharingContainer.hide()
            screenSharingContainer.removeAllViews()
        } else {
            participantsGrid.hide()
            participantsGrid.removeAllViews()
        }
        participantsAdapter.notifyDataSetChanged()
    }

    private fun screenAnnotations() {
        try {
            if (isScreenSharing) {
                screenAnnotationsView = AnnotationsView(this, otWrapper.session, otConfig.apiKey, true)
                //size of annotations screen, by default will be all the screen
                //take into account the call toolbar as well
                callToolbar.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                annotationsToolbar.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                annotationsToolbar.getChildAt(0)
                    .measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED) //color toolbar
                val display = Point()
                windowManager.defaultDisplay.getSize(display)
                val height =
                    display.y - annotationsToolbar.measuredHeight - callToolbar.measuredHeight - annotationsToolbar.getChildAt(
                            0
                    ).measuredHeight
                val width = window.decorView.rootView.width

                screenAnnotationsView?.layoutParams = ViewGroup.LayoutParams(width, height)
                screenAnnotationsView?.attachToolbar(annotationsToolbar)
                screenAnnotationsView?.videoRenderer = screenSharingRenderer
                screenAnnotationsView?.setAnnotationsListener(this)

                screenSharingView.addView(screenAnnotationsView)
                actionBarFragment.enableAnnotations(true)
                showScreenSharingBar(true)
            }
        } catch (e: Exception) {
            Log.i(LOG_TAG, "Exception - enableRemoteAnnotations: $e")
        }
    }

    private fun remoteAnnotations() {
        try {
            remoteAnnotationsView = AnnotationsView(
                    this, otWrapper.session, otConfig.apiKey,
                    otWrapper.getRemoteConnId(screenRemoteId)
            )
            remoteAnnotationsView?.videoRenderer = remoteRenderer
            remoteAnnotationsView?.attachToolbar(annotationsToolbar)
            remoteAnnotationsView?.setAnnotationsListener(this)
            screenSharingContainer.addView(remoteAnnotationsView)
            actionBarFragment.enableAnnotations(true)
            actionBarFragment.showAnnotations(true)
        } catch (e: Exception) {
            Log.i(LOG_TAG, "Exception - enableRemoteAnnotations: $e")
        }
    }

    private fun dpToPx(dp: Int): Int {
        val screenDensity = resources.displayMetrics.density.toDouble()
        return (screenDensity * dp.toDouble()).toInt()
    }

    private fun forceLandscape() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    private fun restartOrientation() {
        requestedOrientation = orientation
    }

    private fun restartTextChatLayout(restart: Boolean) {
        val params = textChatContainer.layoutParams as RelativeLayout.LayoutParams
        if (restart) {
            //restart to the original size
            params.width = RelativeLayout.LayoutParams.MATCH_PARENT
            params.height = RelativeLayout.LayoutParams.MATCH_PARENT
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0)
        } else {
            //go to the minimized size
            params.height = dpToPx(40)
            params.addRule(RelativeLayout.ABOVE, R.id.actionbar_fragment_container)
            params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
        }
        textChatContainer.layoutParams = params
    }

    private fun addRemoteScreenSharing(remoteId: String, screenView: View) {
        if (otWrapper.getRemoteStreamStatus(remoteId).width > otWrapper.getRemoteStreamStatus(remoteId).height) {
            forceLandscape()
        }
        showAudioVideoCall(false)
        screenSharingContainer.removeAllViews()
        screenSharingContainer.show()
        screenSharingContainer.addView(screenView)
        remoteAnnotations()
        isRemoteAnnotations = true
    }

    private fun removeRemoteScreenSharing() {
        showAudioVideoCall(true)
        isRemoteAnnotations = false
        showAnnotationsToolbar(false)
        restartOrientation()
    }

    private fun addNewParticipant(newParticipant: Participant) {
        participantsList.add(newParticipant)
        if (isCallInProgress) {
            updateParticipantList()
        }
    }

    private fun updateParticipantList() {
        for (i in participantsList.indices) {
            val participant = participantsList[i]
            if (i == 0) {
                val metrics = resources.displayMetrics
                val width = metrics.widthPixels
                val newSize = participantSize
                participant.containerSize = Size(width, newSize.height)
            } else {
                participant.containerSize = participantSize
            }
            participantsList[i] = participant
        }

        participantsAdapter.notifyDataSetChanged()
    }

    private fun updateParticipant(type: Participant.Type, id: String?, audioOnly: Boolean) {
        for (i in participantsList.indices) {
            val participant = participantsList[i]
            if (participant.type == type) {
                if (type == Participant.Type.REMOTE) {
                    if (participant.remoteId == id) {
                        participant.status.setHas(MediaType.VIDEO, audioOnly)
                        participantsList[i] = participant
                    }
                } else {
                    participant.status.setHas(MediaType.VIDEO, audioOnly)
                    participantsList[i] = participant
                }
            }
        }
        participantsAdapter.notifyDataSetChanged()
    }// absolute height in pixels

    // absolute width in pixels
    private val participantSize: Size
        get() {
            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            if (participantsList.size == 2) {
                return Size(width, height / 2)
            } else {
                if (participantsList.size > 2) {
                    return Size(width / 2, height / 2)
                }
            }
            return Size(width, height)
        }

    private fun setupMultipartyLayout() {
        gridLayoutManager.spanSizeLookup = object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                if (participantsList.size == 1) {
                    return 2
                } else {
                    if (participantsList.size == 2) {
                        return if (position == 0) {
                            2
                        } else 1
                    } else {
                        if (participantsList.size == 3) {
                            return if (position == 0 || position == 1) {
                                1
                            } else {
                                2
                            }
                        } else {
                            if (participantsList.size == 4) {
                                return 1
                            } else {
                                if (participantsList.size > 4) {
                                    return if (participantsList.size % 2 != 0) {
                                        if (position == participantsList.size - 1) {
                                            2
                                        } else {
                                            1
                                        }
                                    } else {
                                        1
                                    }
                                }
                            }
                        }
                    }
                }
                return 1
            }
        }
    }

    fun onRemoteVideoChanged(v: View) {
        if (otWrapper.getRemoteStreamStatus(mCurrentRemote).subscribedTo(MediaType.VIDEO)) {
            otWrapper.enableReceivedMedia(mCurrentRemote, MediaType.VIDEO, false)
            (v as ImageButton).setImageResource(R.drawable.no_video_icon)
            updateParticipant(Participant.Type.REMOTE, mCurrentRemote, true)
        } else {
            otWrapper.enableReceivedMedia(mCurrentRemote, MediaType.VIDEO, true)
            (v as ImageButton).setImageResource(R.drawable.video_icon)
            updateParticipant(Participant.Type.REMOTE, mCurrentRemote, false)
        }
    }

    fun onRemoteAudioChanged(v: View) {
        if (otWrapper.getRemoteStreamStatus(mCurrentRemote).subscribedTo(MediaType.AUDIO)) {
            otWrapper.enableReceivedMedia(mCurrentRemote, MediaType.AUDIO, false)
            (v as ImageButton).setImageResource(R.drawable.no_audio_icon)
        } else {
            otWrapper.enableReceivedMedia(mCurrentRemote, MediaType.AUDIO, true)
            (v as ImageButton).setImageResource(R.drawable.audio_icon)
        }
    }
}
