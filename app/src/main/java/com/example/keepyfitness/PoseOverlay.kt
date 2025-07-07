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
        strokeWidth = 6f // Giảm từ 8f xuống 6f
        style = Paint.Style.FILL
        isAntiAlias = false // Đã tắt anti-alias
    }

    var paintLeft = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 3f // Giảm từ 4f xuống 3f
        isAntiAlias = false
    }

    var paintRight = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 3f // Giảm từ 4f xuống 3f
        isAntiAlias = false
    }

    // TĂNG THROTTLING để giảm lag
    private var lastDrawTime = 0L
    private val drawInterval = 100L // Tăng từ 66ms lên 100ms (~10 FPS)

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

        // CHỈ VẼ CÁC ĐIỂM QUAN TRỌNG NHẤT để giảm lag
        pose?.let { currentPose ->
            // Chỉ vẽ core landmarks thay vì tất cả
            val coreLandmarks = listOf(
                // Core points cho exercise detection
                PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
                PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
                PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
                // Bỏ NOSE để giảm số điểm vẽ
            )

            coreLandmarks.forEach { landmarkType ->
                currentPose.getPoseLandmark(landmarkType)?.let { landmark ->
                    canvas.drawPoint(landmark.position.x * scaleX, landmark.position.y * scaleY, paint)
                }
            }
        }

        // VẼ SKELETON THIẾT YẾU - giảm số lines để tăng performance
        // ARMS - quan trọng cho push-ups
        drawPoseLines(canvas,PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_ELBOW, paintLeft)
        drawPoseLines(canvas,PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_SHOULDER, paintLeft)
        drawPoseLines(canvas,PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_ELBOW, paintRight)
        drawPoseLines(canvas,PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_SHOULDER, paintRight)

        // LEGS - quan trọng cho squats, jumping jacks
        drawPoseLines(canvas,PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, paintLeft)
        drawPoseLines(canvas,PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, paintLeft)
        drawPoseLines(canvas,PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, paintRight)
        drawPoseLines(canvas,PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, paintRight)

        // CORE BODY LINES - quan trọng cho torso detection
        drawPoseLines(canvas,PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, paintLeft)
        drawPoseLines(canvas,PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP, paintLeft)
        drawPoseLines(canvas,PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_SHOULDER, paintLeft)
        drawPoseLines(canvas,PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_SHOULDER, paintRight)
    }

    fun drawPoseLines(canvas: Canvas, startPoint: Int, endPoint: Int, paint: Paint) {
        var pointStart = pose?.getPoseLandmark(startPoint)
        var pointEnd = pose?.getPoseLandmark(endPoint)

        if (pointStart != null && pointEnd != null) {
            canvas.drawLine(pointStart.position.x * scaleX, pointStart.position.y * scaleY, pointEnd.position.x * scaleX, pointEnd.position.y * scaleY, paint)
        }
    }

}