package com.example.keepyfitness

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.example.keepyfitness.Model.ExerciseDataModel

class HomeScreen : AppCompatActivity() {

    private lateinit var exerciseListView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        exerciseListView = findViewById(R.id.exerciseList)

        var list = listOf(
            ExerciseDataModel("Push Up", R.drawable.pushup, 1, Color.parseColor("#0041a8")),
            ExerciseDataModel("Squat", R.drawable.squat, 2, Color.parseColor("#f20226")),
            ExerciseDataModel("Jumping jack", R.drawable.jumping, 3, Color.parseColor("#f7680f")),
            ExerciseDataModel("Plank to Downward Dog", R.drawable.plank, 4, Color.parseColor("#008a40")),
        )

        var adapter = ExerciseAdapter(this, list)
        exerciseListView.adapter = adapter
    }

    class ExerciseAdapter(val context: Context, val exerciseList: List<ExerciseDataModel>): BaseAdapter(){
        override fun getCount(): Int {
            return exerciseList.size
        }

        override fun getItem(position: Int): Any? {
            return exerciseList[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup?
        ): View? {
            var view = LayoutInflater.from(context).inflate(R.layout.exercise_item, parent, false)
            var titleTV = view.findViewById<TextView>(R.id.textView2)
            var exerciseImg = view.findViewById<ImageView>(R.id.imageView)
            var card = view.findViewById<CardView>(R.id.cardView)

            card.setOnClickListener {
                var intent = Intent(context, MainActivity::class.java)
                intent.putExtra("data", exerciseList[position])
                context.startActivity(intent)
            }

            card.setCardBackgroundColor(exerciseList[position].color)

            Glide.with(context).asGif().load(exerciseList[position].image).into(exerciseImg)

            titleTV.setText(exerciseList[position].title)
            return view
        }

    }

}