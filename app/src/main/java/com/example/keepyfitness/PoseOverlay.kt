package com.example.keepyfitness

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose

class PoseOverlay(context:Context?, attr: AttributeSet?): View(context, attr) {
    private var pose: Pose? = null

    var paint = Paint().apply {
        color = Color.RED
        strokeWidth = 10f
        style = Paint.Style.FILL
    }

    fun setPose(pose: Pose) {
        this.pose = pose
        invalidate() // Redraw the view when the pose is updated
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        pose?.allPoseLandmarks?.forEach {
            canvas.drawPoint(it.position.x, it.position.y, paint)
        }
    }


}