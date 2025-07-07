package com.example.keepyfitness

import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.media.ImageReader
import android.content.Context
import android.media.ImageReader.OnImageAvailableListener
import android.view.Surface
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import android.graphics.Bitmap
import android.media.Image
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import com.example.keepyfitness.Model.ExerciseDataModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class MainActivity : AppCompatActivity(), OnImageAvailableListener {

    val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()
    val poseDetector = PoseDetection.getClient(options)
    private lateinit var poseOverlay: PoseOverlay
    private lateinit var countTV: TextView
    private lateinit var exerciseDataModel: ExerciseDataModel

    // Workout tracking variables
    private var workoutStartTime: Long = 0
    private var targetCount: Int = 0

    // Form Correction variables
    private lateinit var formCorrector: FormCorrector
    private var feedbackContainer: android.widget.LinearLayout? = null
    private var feedbackCard: androidx.cardview.widget.CardView? = null
    private var feedbackIcon: android.widget.ImageView? = null
    private var feedbackTitle: android.widget.TextView? = null
    private var feedbackMessage: android.widget.TextView? = null
    private var formQualityProgress: android.widget.ProgressBar? = null
    private var formQualityText: android.widget.TextView? = null
    private var lastFeedbackTime = 0L
    private val feedbackCooldown = 3000L

    // Voice Coach
    private lateinit var voiceCoach: VoiceCoach
    private var lastMotivationTime = 0L
    private val motivationInterval = 30000L
    private var workoutAnnounced = false

    // Camera variables
    var previewHeight = 0
    var previewWidth = 0
    var sensorOrientation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        exerciseDataModel = intent.getSerializableExtra("data") as ExerciseDataModel
        targetCount = intent.getIntExtra("target_count", 50)
        workoutStartTime = System.currentTimeMillis()

        // Initialize components
        formCorrector = FormCorrector()
        voiceCoach = VoiceCoach(this)

        poseOverlay = findViewById(R.id.po)
        countTV = findViewById<TextView>(R.id.textView)
        var countCard = findViewById<CardView>(R.id.countCard)

        setupFormFeedbackOverlay()
        countCard.setBackgroundColor(exerciseDataModel.color)

        var topCard = findViewById<CardView>(R.id.card2)
        topCard.setBackgroundColor(exerciseDataModel.color)

        var topImg = findViewById<ImageView>(R.id.imageView2)
        Glide.with(applicationContext).asGif().load(exerciseDataModel.image).into(topImg)

        // Request camera permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                val permission = arrayOf(android.Manifest.permission.CAMERA)
                requestPermissions(permission, 1122)
            } else {
                setFragment()
            }
        } else {
            setFragment()
        }
    }

    private fun setupFormFeedbackOverlay() {
        val inflater = layoutInflater
        val feedbackOverlay = inflater.inflate(R.layout.form_feedback_overlay, null)

        feedbackContainer = feedbackOverlay.findViewById(R.id.feedbackContainer)
        feedbackCard = feedbackOverlay.findViewById(R.id.feedbackCard)
        feedbackIcon = feedbackOverlay.findViewById(R.id.feedbackIcon)
        feedbackTitle = feedbackOverlay.findViewById(R.id.feedbackTitle)
        feedbackMessage = feedbackOverlay.findViewById(R.id.feedbackMessage)
        formQualityProgress = feedbackOverlay.findViewById(R.id.formQualityProgress)
        formQualityText = feedbackOverlay.findViewById(R.id.formQualityText)

        val mainLayout = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.main)
        val layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.topToBottom = R.id.card2
        layoutParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        layoutParams.setMargins(0, 16, 0, 0)

        feedbackOverlay.layoutParams = layoutParams
        mainLayout.addView(feedbackOverlay)

        feedbackOverlay.findViewById<android.widget.ImageView>(R.id.dismissFeedback).setOnClickListener {
            hideFeedback()
        }
    }

    private fun showFeedback(feedback: String, isPositive: Boolean, formQuality: Int) {
        if (System.currentTimeMillis() - lastFeedbackTime < feedbackCooldown) return
        lastFeedbackTime = System.currentTimeMillis()

        feedbackContainer?.visibility = android.view.View.VISIBLE
        feedbackCard?.setCardBackgroundColor(
            if (isPositive) android.graphics.Color.parseColor("#4CAF50")
            else android.graphics.Color.parseColor("#F44336")
        )
        feedbackIcon?.setImageResource(
            if (isPositive) android.R.drawable.ic_dialog_info
            else android.R.drawable.ic_dialog_alert
        )
        feedbackTitle?.text = if (isPositive) "Good Job!" else "Correction Needed!"
        feedbackMessage?.text = feedback

        formQualityProgress?.progress = formQuality
        formQualityText?.text = "$formQuality%"

        val progressColor = when {
            formQuality >= 80 -> android.graphics.Color.parseColor("#4CAF50")
            formQuality >= 60 -> android.graphics.Color.parseColor("#FF9800")
            else -> android.graphics.Color.parseColor("#F44336")
        }
        formQualityProgress?.progressTintList = android.content.res.ColorStateList.valueOf(progressColor)

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            hideFeedback()
        }, 4000)
    }

    private fun hideFeedback() {
        feedbackContainer?.visibility = android.view.View.GONE
    }

    protected fun setFragment() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        try {
            cameraId = manager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        val camera2Fragment = CameraConnectionFragment.newInstance(
            object : CameraConnectionFragment.ConnectionCallback {
                override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
                    previewHeight = size!!.height
                    previewWidth = size.width
                    poseOverlay.imageHeight = previewHeight
                    sensorOrientation = rotation - getScreenOrientation()
                    poseOverlay.sensorOrientation = sensorOrientation
                }

                override fun onTextureViewChosen(width: Int, height: Int) {
                    poseOverlay.videoWidth = width
                    poseOverlay.videoHeight = height
                }
            },
            this,
            R.layout.camera_fragment,
            Size(640, 480)
        )
        camera2Fragment.setCamera(cameraId)
        supportFragmentManager.beginTransaction().replace(R.id.container, camera2Fragment).commit()
    }

    protected fun getScreenOrientation(): Int {
        return when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_270 -> 270
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_90 -> 90
            else -> 0
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setFragment()
        } else {
            finish()
        }
    }

    // Camera frame processing - TĂNG HIỆU SUẤT
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    private var rgbFrameBitmap: Bitmap? = null

    // THROTTLING VARIABLES để giảm lag
    private var lastProcessTime = 0L
    private val processInterval = 200L // Giảm từ real-time xuống 5 FPS
    private var frameSkipCounter = 0
    private val frameSkipInterval = 3 // Chỉ xử lý 1/3 frames

    override fun onImageAvailable(reader: ImageReader) {
        if (previewWidth == 0 || previewHeight == 0) return

        // SKIP FRAMES để tăng performance
        frameSkipCounter++
        if (frameSkipCounter % frameSkipInterval != 0) {
            reader.acquireLatestImage()?.close()
            return
        }

        // THROTTLE processing để tránh overload
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessTime < processInterval) {
            reader.acquireLatestImage()?.close()
            return
        }
        lastProcessTime = currentTime

        if (rgbBytes == null) {
            rgbBytes = IntArray(previewWidth * previewHeight)
        }
        try {
            val image = reader.acquireLatestImage() ?: return
            if (isProcessingFrame) {
                image.close()
                return
            }
            isProcessingFrame = true
            val planes = image.planes
            fillBytes(planes, yuvBytes)
            yRowStride = planes[0].rowStride
            val uvRowStride = planes[1].rowStride
            val uvPixelStride = planes[1].pixelStride
            imageConverter = Runnable {
                ImageUtils.convertYUV420ToARGB8888(
                    yuvBytes[0]!!,
                    yuvBytes[1]!!,
                    yuvBytes[2]!!,
                    previewWidth,
                    previewHeight,
                    yRowStride,
                    uvRowStride,
                    uvPixelStride,
                    rgbBytes!!
                )
            }
            postInferenceCallback = Runnable {
                image.close()
                isProcessingFrame = false
            }
            processImage()
        } catch (e: Exception) {
            postInferenceCallback?.run()
        }
    }

    private fun processImage() {
        imageConverter!!.run()
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        rgbFrameBitmap?.setPixels(rgbBytes!!, 0, previewWidth, 0, 0, previewWidth, previewHeight)

        val inputImage = InputImage.fromBitmap(rgbFrameBitmap!!, sensorOrientation)

        poseDetector.process(inputImage)
            .addOnSuccessListener { results ->
                poseOverlay.setPose(results)

                // Announce workout start (once)
                if (!workoutAnnounced) {
                    voiceCoach.announceWorkoutStart(exerciseDataModel.title, targetCount)
                    workoutAnnounced = true
                }

                // GIẢM TẦN SUẤT form analysis để tăng performance
                if (frameSkipCounter % 6 == 0) { // Chỉ analyze form mỗi 6 frames
                    analyzeFormAndGiveFeedback(results)
                }

                // Exercise detection và counting - GIỮ NGUYÊN tần suất cho accuracy
                detectAndCountExercise(results)
            }
            .addOnFailureListener { e ->
                Log.e("PoseDetection", "Failed to process image", e)
            }

        postInferenceCallback!!.run()
    }

    // TÁCH RIÊNG form analysis để optimize
    private fun analyzeFormAndGiveFeedback(results: Pose) {
        val feedbacks = formCorrector.analyzeForm(exerciseDataModel.id, results)
        val formQuality = formCorrector.calculateFormQuality(exerciseDataModel.id, results)

        runOnUiThread {
            formQualityProgress?.progress = formQuality
            formQualityText?.text = "$formQuality%"
        }

        // Voice feedback for form corrections - THROTTLED
        val criticalFeedback = feedbacks.firstOrNull {
            it.severity == com.example.keepyfitness.Model.FeedbackSeverity.CRITICAL
        }
        val warningFeedback = feedbacks.firstOrNull {
            it.severity == com.example.keepyfitness.Model.FeedbackSeverity.WARNING
        }
        val positiveFeedback = feedbacks.firstOrNull {
            it.severity == com.example.keepyfitness.Model.FeedbackSeverity.INFO &&
            (it.message.contains("Perfect") || it.message.contains("Great") || it.message.contains("Tuyệt vời"))
        }

        when {
            criticalFeedback != null -> {
                runOnUiThread {
                    showFeedback(criticalFeedback.message, false, formQuality)
                    voiceCoach.giveFormFeedback(criticalFeedback.message, false)
                }
            }
            warningFeedback != null -> {
                runOnUiThread {
                    showFeedback(warningFeedback.message, false, formQuality)
                    voiceCoach.giveFormFeedback(warningFeedback.message, false)
                }
            }
            positiveFeedback != null -> {
                runOnUiThread {
                    showFeedback(positiveFeedback.message, true, formQuality)
                    voiceCoach.giveFormFeedback(positiveFeedback.message, true)
                }
            }
        }
    }

    // TÁCH RIÊNG exercise detection để tối ưu
    private fun detectAndCountExercise(results: Pose) {
        var currentCount = 0
        when(exerciseDataModel.id) {
            1 -> {
                val oldCount = pushUpCount
                detectPushUp(results)
                currentCount = pushUpCount
                if (pushUpCount > oldCount) {
                    voiceCoach.announceCount(pushUpCount, "push ups")
                    voiceCoach.announceProgress(pushUpCount, targetCount)
                }
                runOnUiThread { countTV.text = pushUpCount.toString() }
            }
            2 -> {
                val oldCount = squatCount
                detectSquat(results)
                currentCount = squatCount
                if (squatCount > oldCount) {
                    voiceCoach.announceCount(squatCount, "squats")
                    voiceCoach.announceProgress(squatCount, targetCount)
                }
                runOnUiThread { countTV.text = squatCount.toString() }
            }
            3 -> {
                val oldCount = jumpingJackCount
                detectJumpingJack(results)
                currentCount = jumpingJackCount
                if (jumpingJackCount > oldCount) {
                    voiceCoach.announceCount(jumpingJackCount, "jumping jacks")
                    voiceCoach.announceProgress(jumpingJackCount, targetCount)
                }
                runOnUiThread { countTV.text = jumpingJackCount.toString() }
            }
            4 -> {
                val oldCount = plankDogCount
                detectPlankToDownwardDog(results)
                currentCount = plankDogCount
                if (plankDogCount > oldCount) {
                    voiceCoach.announceCount(plankDogCount, "plank transitions")
                    voiceCoach.announceProgress(plankDogCount, targetCount)
                }
                runOnUiThread { countTV.text = plankDogCount.toString() }
            }
        }

        // Motivational messages - GIẢM TẦN SUẤT
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMotivationTime > motivationInterval && currentCount > 0) {
            voiceCoach.giveMotivation()
            lastMotivationTime = currentTime
        }
    }

    protected fun fillBytes(planes: Array<Image.Plane>, yuvBytes: Array<ByteArray?>) {
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]]
        }
    }

    // Exercise detection methods
    var pushUpCount = 0
    var isLowered = false
    fun detectPushUp(pose: Pose) {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        if (leftShoulder == null || rightShoulder == null ||
            leftElbow == null || rightElbow == null ||
            leftWrist == null || rightWrist == null ||
            leftHip == null || rightHip == null) return

        val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
        val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
        val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2.0

        val knee = leftKnee ?: rightKnee ?: return
        val torsoAngle = calculateAngle(leftShoulder, leftHip, knee)
        val inPlankPosition = torsoAngle > 160 && torsoAngle < 180

        if (avgElbowAngle < 90 && inPlankPosition) {
            isLowered = true
        } else if (avgElbowAngle > 160 && isLowered && inPlankPosition) {
            pushUpCount++
            isLowered = false
        }
    }

    var squatCount = 0
    var isSquatting = false
    fun detectSquat(pose: Pose) {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (leftHip == null || rightHip == null ||
            leftKnee == null || rightKnee == null ||
            leftAnkle == null || rightAnkle == null ||
            leftShoulder == null || rightShoulder == null) return

        val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2.0

        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
        val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2
        val hipBelowKnee = avgHipY > avgKneeY

        val leftTorsoAngle = calculateAngle(leftShoulder, leftHip, leftKnee)
        val rightTorsoAngle = calculateAngle(rightShoulder, rightHip, rightKnee)
        val avgTorsoAngle = (leftTorsoAngle + rightTorsoAngle) / 2.0

        if (avgKneeAngle < 90 && avgTorsoAngle > 80 && hipBelowKnee) {
            isSquatting = true
        } else if (avgKneeAngle > 160 && isSquatting) {
            squatCount++
            isSquatting = false
        }
    }

    var jumpingJackCount = 0
    var isHandsUpAndLegsApart = false
    var isInStartPosition = false
    fun detectJumpingJack(pose: Pose) {
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (leftWrist == null || rightWrist == null ||
            leftAnkle == null || rightAnkle == null ||
            leftHip == null || rightHip == null ||
            leftShoulder == null || rightShoulder == null) return

        val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
        val avgWristY = (leftWrist.position.y + rightWrist.position.y) / 2
        val handsAboveShoulders = avgWristY < avgShoulderY - 30

        val hipWidth = distance(leftHip, rightHip)
        val ankleDistance = distance(leftAnkle, rightAnkle)
        val legsApart = ankleDistance > hipWidth * 1.5

        val handsDown = avgWristY > avgShoulderY + 20
        val legsTogether = ankleDistance <= hipWidth * 1.2

        if (handsDown && legsTogether) {
            isInStartPosition = true
        }

        if (handsAboveShoulders && legsApart && isInStartPosition) {
            isHandsUpAndLegsApart = true
        } else if (handsDown && legsTogether && isHandsUpAndLegsApart) {
            jumpingJackCount++
            isHandsUpAndLegsApart = false
        }
    }

    var plankDogCount = 0
    var isInPlank = false
    fun detectPlankToDownwardDog(pose: Pose) {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        if (leftShoulder == null || rightShoulder == null ||
            leftHip == null || rightHip == null ||
            leftAnkle == null || rightAnkle == null ||
            leftWrist == null || rightWrist == null) return

        val shoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
        val hipY = (leftHip.position.y + rightHip.position.y) / 2
        val ankleY = (leftAnkle.position.y + rightAnkle.position.y) / 2
        val wristY = (leftWrist.position.y + rightWrist.position.y) / 2

        val bodyAlignment = kotlin.math.abs(shoulderY - hipY) < 30 && kotlin.math.abs(hipY - ankleY) < 30
        val handsOnGround = kotlin.math.abs(wristY - shoulderY) < 50
        val inPlankPosition = bodyAlignment && handsOnGround

        val hipsElevated = hipY < shoulderY - 50 && hipY < ankleY - 30
        val inDownwardDog = hipsElevated && handsOnGround

        if (inPlankPosition && !isInPlank) {
            isInPlank = true
        } else if (isInPlank && inDownwardDog) {
            plankDogCount++
            isInPlank = false
        }
    }

    fun calculateAngle(first: PoseLandmark, mid: PoseLandmark, last: PoseLandmark): Double {
        val a = distance(mid, last)
        val b = distance(first, mid)
        val c = distance(first, last)
        return kotlin.math.acos((b * b + a * a - c * c) / (2 * b * a)) * (180 / kotlin.math.PI)
    }

    fun distance(p1: PoseLandmark, p2: PoseLandmark): Double {
        val dx = p1.position.x - p2.position.x
        val dy = p1.position.y - p2.position.y
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceCoach.shutdown()
    }
}
