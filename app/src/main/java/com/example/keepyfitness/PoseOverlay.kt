package com.example.keepyfitness

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class PoseOverlay(context:Context?, attr: AttributeSet?): View(context, attr) {
    var imageWidth:Int = 640
    var imageHeight:Int = 480

    private var scaleX: Float = 0f
    private var scaleY: Float = 0f

    var videoWidth: Int = width
    var videoHeight: Int = height

    var sensorOrientation: Int = 0

    private var pose: Pose? = null

    // TĂNG HIỆU SUẤT DRAWING - giảm lag vẽ pose overlay
    var paint = Paint().apply {
        color = Color.RED
        strokeWidth = 4f // Giảm từ 6f xuống 4f
        style = Paint.Style.FILL
        isAntiAlias = false // Tắt anti-alias để tăng performance
    }

    var paintLeft = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 2f // Giảm từ 3f xuống 2f
        isAntiAlias = false
    }

    var paintRight = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 2f // Giảm từ 3f xuống 2f
        isAntiAlias = false
    }

    // AGGRESSIVE THROTTLING để giảm lag
    private var lastDrawTime = 0L
    private val drawInterval = 150L // Tăng từ 100ms lên 150ms (~6.7 FPS)

    fun setPose(pose: Pose) {
        this.pose = pose

        // THROTTLE invalidate để tránh overload UI thread
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDrawTime > drawInterval) {
            invalidate()
            lastDrawTime = currentTime
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if(sensorOrientation == 90 || sensorOrientation == 270) {
            scaleX = videoWidth/imageHeight.toFloat()
            scaleY = videoHeight/imageWidth.toFloat()
        } else {
            scaleX = videoHeight/imageHeight.toFloat()
            scaleY = videoWidth/imageWidth.toFloat()
        }

        scaleX = videoWidth/imageHeight.toFloat()
        scaleY = videoHeight/imageWidth.toFloat()

        // CHỈ VẼ CÁC ĐIỂM CORE QUAN TRỌNG NHẤT để giảm lag
        pose?.let { currentPose ->
            // Chỉ vẽ 8 điểm core thay vì 12 điểm
            val corePoints = listOf(
                PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE
                // Bỏ wrists và ankles để giảm số điểm vẽ
            )

            corePoints.forEach { landmarkType ->
                currentPose.getPoseLandmark(landmarkType)?.let { landmark ->
                    canvas.drawCircle(
                        landmark.position.x * scaleX,
                        landmark.position.y * scaleY,
                        4f, // Giảm radius từ point thành circle nhỏ
                        paint
                    )
                }
            }
        }

        // VẼ SKELETON TỐI THIỂU - chỉ những lines quan trọng nhất
        // CORE BODY LINES - quan trọng cho torso detection
        drawPoseLines(canvas,PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, paintLeft)
        drawPoseLines(canvas,PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, paintLeft)
        drawPoseLines(canvas,PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_SHOULDER, paintLeft)
        drawPoseLines(canvas,PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_SHOULDER, paintRight)

        // CHỈ VẼ ARMS quan trọng cho push-ups - bỏ wrists
        drawPoseLines(canvas,PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_SHOULDER, paintLeft)
        drawPoseLines(canvas,PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_SHOULDER, paintRight)

        // CHỈ VẼ LEGS quan trọng cho squats - bỏ ankles
        drawPoseLines(canvas,PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, paintLeft)
        drawPoseLines(canvas,PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, paintRight)
    }

    fun drawPoseLines(canvas: Canvas, startPoint: Int, endPoint: Int, paint: Paint) {
        var pointStart = pose?.getPoseLandmark(startPoint)
        var pointEnd = pose?.getPoseLandmark(endPoint)

        if (pointStart != null && pointEnd != null) {
            canvas.drawLine(
                pointStart.position.x * scaleX,
                pointStart.position.y * scaleY,
                pointEnd.position.x * scaleX,
                pointEnd.position.y * scaleY,
                paint
            )
        }
    }

}