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

    private var pose: Pose? = null

    var paint = Paint().apply {
        color = Color.RED
        strokeWidth = 10f
        style = Paint.Style.FILL
    }

    var paintLeft = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    var paintRight = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }


    fun setPose(pose: Pose) {
        this.pose = pose
        invalidate() // Redraw the view when the pose is updated
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        scaleX = videoWidth/imageHeight.toFloat()
        scaleY = videoHeight/imageWidth.toFloat()

        pose?.allPoseLandmarks?.forEach {
            canvas.drawPoint(it.position.x * scaleX, it.position.y * scaleY, paint)
        }

        // DRAW ARMS
        drawPoseLines(canvas,PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_ELBOW, paintLeft)
        drawPoseLines(canvas,PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_SHOULDER, paintLeft)
        drawPoseLines(canvas,PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_ELBOW, paintRight)
        drawPoseLines(canvas,PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_SHOULDER, paintRight)

        // DRAW LEGS
        drawPoseLines(canvas,PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, paintLeft)
        drawPoseLines(canvas,PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE, paintLeft)
        drawPoseLines(canvas,PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, paintRight)
        drawPoseLines(canvas,PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE, paintRight)

        // DRAW BODY
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