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
    // Base pose detector with streaming frames, when depending on the pose-detection sdk
    val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()
    val poseDetector = PoseDetection.getClient(options)
    private lateinit var poseOverlay: PoseOverlay
    private lateinit var countTV: TextView
    private lateinit var exerciseDataModel: ExerciseDataModel

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

        poseOverlay = findViewById(R.id.po)
        countTV = findViewById<TextView>(R.id.textView)
        var countCard = findViewById<CardView>(R.id.countCard)
        countCard.setBackgroundColor(exerciseDataModel.color)

        var topCard = findViewById<CardView>(R.id.card2)
        topCard.setBackgroundColor(exerciseDataModel.color)

        var topImg = findViewById<ImageView>(R.id.imageView2)
        Glide.with(applicationContext).asGif().load(exerciseDataModel.image).into(topImg)


        //TODO ask for permission of camera upon first launch of application
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED
            ) {
                val permission = arrayOf(
                    android.Manifest.permission.CAMERA

                )
                requestPermissions(permission, 1122)
            } else {
                //TODO show live camera footage
                setFragment()

            }
        }else{
            //TODO show live camera footage
            setFragment()
        }
    }

    var previewHeight = 0;
    var previewWidth = 0
    var sensorOrientation = 0;
    //TODO fragment which show llive footage from camera
    protected fun setFragment() {
        val manager =
            getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        try {
            cameraId = manager.cameraIdList[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        val fragment: Fragment
        val camera2Fragment = CameraConnectionFragment.newInstance(
            object :
                CameraConnectionFragment.ConnectionCallback {
                override fun onPreviewSizeChosen(size: Size?, rotation: Int) {
                    previewHeight = size!!.height
                    previewWidth = size.width
                    poseOverlay.imageWidth = previewWidth
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
        fragment = camera2Fragment
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
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
        //TODO show live camera footage
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //TODO show live camera footage
            setFragment()
        } else {
            finish()
        }
    }

    //TODO getting frames of live camera footage and passing them to model
    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var rgbBytes: IntArray? = null
    private var yRowStride = 0
    private var postInferenceCallback: Runnable? = null
    private var imageConverter: Runnable? = null
    private var rgbFrameBitmap: Bitmap? = null
    override fun onImageAvailable(reader: ImageReader) {
        // We need wait until we have some size from onPreviewSizeChosen
        if (previewWidth == 0 || previewHeight == 0) {
            return
        }
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
            return
        }
    }
    private fun processImage() {
        imageConverter!!.run()
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        rgbFrameBitmap?.setPixels(rgbBytes!!, 0, previewWidth, 0, 0, previewWidth, previewHeight)

        var inputImage =  InputImage.fromBitmap(rgbFrameBitmap!!, sensorOrientation)

        poseDetector.process(inputImage)
            .addOnSuccessListener { results ->
                // Task completed successfully
                // ...
                Log.d("tryPose", results.getPoseLandmark(PoseLandmark.LEFT_KNEE)?.position?.x.toString())
                poseOverlay.setPose(results)

                if(exerciseDataModel.id == 1) {
                    detectPushUp(results)
                    runOnUiThread {
                        countTV.setText(pushUpCount.toString())
                    }
                } else if (exerciseDataModel.id == 2) {
                    detectSquat(results)
                    runOnUiThread {
                        countTV.setText(squatCount.toString())
                    }
                } else if (exerciseDataModel.id == 3) {
                    detectJumpingJack(results)
                    runOnUiThread {
                        countTV.setText(jumpingJackCount.toString())
                    }
                } else if (exerciseDataModel.id == 4) {
                    detectPlankToDownwardDog(results)
                    runOnUiThread {
                        countTV.setText(plankDogCount.toString())
                    }
                }

            }
            .addOnFailureListener { e ->
                // Task failed with an exception
                // ...
            }


        postInferenceCallback!!.run()
    }
    protected fun fillBytes(
        planes: Array<Image.Plane>,
        yuvBytes: Array<ByteArray?>
    ) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (i in planes.indices) {
            val buffer = planes[i].buffer
            if (yuvBytes[i] == null) {
                yuvBytes[i] = ByteArray(buffer.capacity())
            }
            buffer[yuvBytes[i]]
        }
    }

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
            leftHip == null || rightHip == null) {
            //Log.d("PushUpDetector", "Missing landmarks, skipping frame")
            return
        }

        val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
        val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)
        val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2.0

        val knee = leftKnee ?: rightKnee ?: return
        val torsoAngle = calculateAngle(leftShoulder, leftHip, knee)

        val inPlankPosition = torsoAngle > 160 && torsoAngle < 180

        // 🔍 Logging angles and states
        Log.d("PushUpDetector", "LeftElbow: $leftElbowAngle, RightElbow: $rightElbowAngle, Avg: $avgElbowAngle")
        //Log.d("PushUpDetector", "TorsoAngle: $torsoAngle, InPlank: $inPlankPosition")
        //Log.d("PushUpDetector", "IsLowered: $isLowered, PushUpCount: $pushUpCount")

        if (avgElbowAngle < 90 && inPlankPosition) {
            isLowered = true
            //Log.d("PushUpDetector", "Detected lowering phase")
        } else if (avgElbowAngle > 160 && isLowered && inPlankPosition) {
            pushUpCount++
            isLowered = false
            //Log.d("PushUpDetector", "Push-up counted! Total: $pushUpCount")
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
            leftShoulder == null || rightShoulder == null) {
            return
        }

        val leftKneeAngle = calculateAngle(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = calculateAngle(rightHip, rightKnee, rightAnkle)
        val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2.0

        val leftTorsoAngle = calculateAngle(leftShoulder, leftHip, leftKnee)
        val rightTorsoAngle = calculateAngle(rightShoulder, rightHip, rightKnee)
        val avgTorsoAngle = (leftTorsoAngle + rightTorsoAngle) / 2.0

        // Logging for debugging
        Log.d("SquatDetector", "LeftKnee: $leftKneeAngle, RightKnee: $rightKneeAngle, Avg: $avgKneeAngle")
        Log.d("SquatDetector", "LeftTorso: $leftTorsoAngle, RightTorso: $rightTorsoAngle, Avg: $avgTorsoAngle")
        Log.d("SquatDetector", "isSquatting: $isSquatting, SquatCount: $squatCount")

        // Detect squat: knee angle < 100 and torso not too bent
        if (avgKneeAngle < 100 && avgTorsoAngle > 60) {
            isSquatting = true
        } else if (avgKneeAngle > 160 && isSquatting) {
            squatCount++
            isSquatting = false
        }
    }

    var jumpingJackCount = 0
    var isHandsUpAndLegsApart = false
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
            leftShoulder == null || rightShoulder == null) {
            return
        }

        // Hands above shoulders? (Smaller y means higher on screen)
        val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
        val avgWristY = (leftWrist.position.y + rightWrist.position.y) / 2
        val handsAboveShoulders = avgWristY < avgShoulderY - 30

        // Legs apart? (Distance between ankles significantly wider than hips)
        val hipWidth = distance(leftHip, rightHip)
        val ankleDistance = distance(leftAnkle, rightAnkle)
        val legsApart = ankleDistance > hipWidth * 1.5

        // Logging for debugging
        Log.d("JumpingJackDetector", "handsAbove: $handsAboveShoulders, legsApart: $legsApart")

        if (handsAboveShoulders && legsApart) {
            isHandsUpAndLegsApart = true
        } else if (!handsAboveShoulders && !legsApart && isHandsUpAndLegsApart) {
            jumpingJackCount++
            isHandsUpAndLegsApart = false
            Log.d("JumpingJackDetector", "Jumping Jack Counted! Total: $jumpingJackCount")
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

        if (leftShoulder == null || rightShoulder == null ||
            leftHip == null || rightHip == null ||
            leftAnkle == null || rightAnkle == null) {
            return
        }

        // Average positions
        val shoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
        val hipY = (leftHip.position.y + rightHip.position.y) / 2
        val ankleY = (leftAnkle.position.y + rightAnkle.position.y) / 2

        // Measure vertical changes
        val hipToShoulder = hipY - shoulderY
        val hipToAnkle = hipY - ankleY

        // Detect Plank: hips nearly aligned between shoulders and ankles (flat body)
        val inPlankPosition = Math.abs(hipToShoulder) < 50 && Math.abs(hipToAnkle) < 50

        // Detect Downward Dog: hips much higher than shoulders and ankles
        val inDownwardDog = hipY < shoulderY - 50 && hipY < ankleY - 50

        // Debug Logging
        Log.d("PlankDogDetector", "hipY: $hipY, shoulderY: $shoulderY, ankleY: $ankleY")
        Log.d("PlankDogDetector", "inPlank: $inPlankPosition, inDownwardDog: $inDownwardDog")

        // Transition detection
        if (inPlankPosition) {
            isInPlank = true
        } else if (isInPlank && inDownwardDog) {
            plankDogCount++
            isInPlank = false
            Log.d("PlankDogDetector", "Plank to Downward Dog Counted! Total: $plankDogCount")
        }
    }

    // Helper: Calculate angle between three points
    fun calculateAngle(first: PoseLandmark, mid: PoseLandmark, last: PoseLandmark): Double {
        val a = distance(mid, last)
        val b = distance(first, mid)
        val c = distance(first, last)

        return Math.acos((b * b + a * a - c * c) / (2 * b * a)) * (180 / Math.PI)
    }
    fun distance(p1: PoseLandmark, p2: PoseLandmark): Double {
        val dx = p1.position.x - p2.position.x
        val dy = p1.position.y - p2.position.y
        return Math.sqrt((dx * dx + dy * dy).toDouble())
    }

}