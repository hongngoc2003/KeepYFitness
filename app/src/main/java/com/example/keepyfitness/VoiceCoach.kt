package com.example.keepyfitness

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*

class VoiceCoach(private val context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceCoach"
    }

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var speechQueue = mutableListOf<String>()

    // THROTTLING cho voice feedback để tránh spam
    private var lastSpeechTime = 0L
    private val speechCooldown = 2000L // 2 giây giữa các voice feedback
    private var lastFormFeedbackTime = 0L
    private val formFeedbackCooldown = 4000L // 4 giây cho form feedback

    init {
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.let { tts ->
                val result = tts.setLanguage(Locale.getDefault())

                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported")
                    // Fallback to English
                    tts.setLanguage(Locale.ENGLISH)
                }

                // TĂNG TỐC ĐỘ để giảm delay
                tts.setSpeechRate(1.1f) // Tăng từ 0.9f lên 1.1f
                tts.setPitch(1.0f)

                isInitialized = true

                // Process queued messages
                processSpeechQueue()

                Log.d(TAG, "TextToSpeech initialized successfully")
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed")
        }
    }

    private fun processSpeechQueue() {
        if (isInitialized && speechQueue.isNotEmpty()) {
            speechQueue.forEach { message ->
                speakNow(message)
            }
            speechQueue.clear()
        }
    }

    private fun speakNow(message: String) {
        // KIỂM TRA COOLDOWN trước khi nói
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSpeechTime < speechCooldown) {
            return // Skip nếu nói quá gần
        }
        lastSpeechTime = currentTime

        textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null) // QUEUE_FLUSH thay vì QUEUE_ADD
    }

    fun speak(message: String) {
        if (isInitialized) {
            speakNow(message)
        } else {
            // CHỈ QUEUE những message quan trọng
            if (speechQueue.size < 3) { // Giới hạn queue size
                speechQueue.add(message)
            }
        }
    }

    // Exercise-specific coaching messages
    fun announceExerciseStart(exerciseName: String) {
        speak("Starting $exerciseName. Get ready!")
    }

    // Thêm phương thức thông báo bắt đầu workout với target
    fun announceWorkoutStart(exerciseName: String, targetCount: Int) {
        speak("Starting $exerciseName workout. Target: $targetCount repetitions. Let's go!")
    }

    // PHƯƠNG THỨC CHÍNH: Thông báo lỗi động tác - TIẾNG ANH
    fun announceFormError(errorMessage: String) {
        speak("Form correction: $errorMessage")
    }

    // THROTTLED form feedback - TIẾNG ANH
    fun giveFormFeedback(feedback: String, isPositive: Boolean) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFormFeedbackTime < formFeedbackCooldown) {
            return // Skip form feedback nếu quá gần
        }
        lastFormFeedbackTime = currentTime

        if (isPositive) {
            speak("Great form! $feedback")
        } else {
            speak("Watch your form: $feedback")
        }
    }

    // Thông báo lỗi cụ thể cho từng bài tập - TOÀN BỘ TIẾNG ANH
    fun announcePushUpErrors(errorType: String) {
        when (errorType) {
            "ELBOW_FLARE" -> speak("Elbows too wide. Keep elbows close to your body")
            "BACK_SAG" -> speak("Keep your back straight. Engage your core")
            "INCOMPLETE_RANGE" -> speak("Go lower. Lower your chest closer to the ground")
            "HEAD_POSITION" -> speak("Look down at the ground. Keep neutral head position")
            else -> speak("Watch your push up form")
        }
    }

    fun announceSquatErrors(errorType: String) {
        when (errorType) {
            "KNEE_CAVE" -> speak("Knees caving in. Push knees out")
            "FORWARD_LEAN" -> speak("Keep chest up. Don't lean forward")
            "HEEL_LIFT" -> speak("Keep feet flat on the ground")
            "SHALLOW_SQUAT" -> speak("Go deeper. Squat down lower")
            "KNEE_FORWARD" -> speak("Knees too far forward. Push hips back")
            else -> speak("Watch your squat form")
        }
    }

    fun announceJumpingJackErrors(errorType: String) {
        when (errorType) {
            "ARMS_LOW" -> speak("Raise arms higher. Hands above your head")
            "FEET_NARROW" -> speak("Jump wider. Spread legs further apart")
            "TIMING_OFF" -> speak("Sync your arms and legs. Move together")
            "INCOMPLETE_RETURN" -> speak("Return to starting position completely")
            else -> speak("Watch your jumping jack form")
        }
    }

    fun announcePlankErrors(errorType: String) {
        when (errorType) {
            "HIP_SAG" -> speak("Hips too low. Lift your hips up")
            "HIP_HIGH" -> speak("Hips too high. Lower your hips")
            "ARM_POSITION" -> speak("Place hands directly under shoulders")
            "HEAD_POSITION" -> speak("Look down at the floor. Keep neutral neck")
            else -> speak("Watch your plank form")
        }
    }

    // Thông báo động viên - TIẾNG ANH
    fun giveMotivation() {
        val motivationalMessages = listOf(
            "You're doing great!",
            "Keep it up!",
            "Stay strong!",
            "Perfect form!",
            "You got this!",
            "Amazing work!",
            "Keep pushing!",
            "Excellent effort!",
            "Don't give up!",
            "You're crushing it!"
        )
        speak(motivationalMessages.random())
    }

    // Thông báo cảnh báo an toàn - TIẾNG ANH
    fun announceSafetyWarning(warning: String) {
        speak("Safety warning: $warning")
    }

    // Thông báo chất lượng động tác - TIẾNG ANH
    fun announceFormQuality(quality: Int) {
        when {
            quality >= 90 -> speak("Excellent form quality!")
            quality >= 80 -> speak("Good form quality!")
            quality >= 70 -> speak("Form quality is okay")
            quality >= 60 -> speak("Need to improve form")
            else -> speak("Focus on your posture")
        }
    }

    // ĐẾM CHỈ SỐ - KHÔNG KÈM TÊN BÀI TẬP
    fun announceCount(count: Int, exerciseName: String) {
        speak("$count") // Chỉ nói số, không nói tên bài tập
    }

    // Thêm phương thức đếm đơn giản chỉ số
    fun announceSimpleCount(count: Int) {
        speak("$count")
    }

    // Thêm phương thức thông báo tiến độ - TIẾNG ANH
    fun announceProgress(current: Int, target: Int) {
        val percentage = (current.toFloat() / target * 100).toInt()
        when {
            current == target -> speak("Target achieved! Excellent work!")
            current == target / 2 -> speak("Halfway there! Keep pushing!")
            percentage % 25 == 0 && percentage > 0 -> speak("$percentage percent complete!")
        }
    }

    fun announceRep(currentRep: Int, totalReps: Int) {
        speak("$currentRep") // Chỉ nói số
        if (currentRep == totalReps) {
            speak("Exercise complete! Great job!")
        } else if (currentRep == totalReps / 2) {
            speak("Halfway there! Keep going!")
        }
    }

    fun announceCountdown(seconds: Int) {
        when (seconds) {
            in 1..5 -> speak("$seconds")
            10 -> speak("10 seconds remaining")
            else -> if (seconds % 10 == 0) speak("$seconds seconds")
        }
    }

    fun announceMotivation() {
        val motivationalMessages = listOf(
            "You're doing great!",
            "Keep it up!",
            "Stay strong!",
            "Perfect form!",
            "You've got this!",
            "Amazing work!",
            "Keep pushing!"
        )
        speak(motivationalMessages.random())
    }

    fun announceFormCorrection(correction: String) {
        speak("Watch your form: $correction")
    }

    fun announceWorkoutComplete() {
        speak("Workout complete! Excellent work today!")
    }

    fun announceRest(seconds: Int) {
        speak("Rest for $seconds seconds")
    }

    fun stop() {
        textToSpeech?.stop()
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        speechQueue.clear()
    }

    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    fun setLanguage(locale: Locale) {
        textToSpeech?.setLanguage(locale)
    }

    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
    }
}
