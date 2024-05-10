package com.example.doottest

import android.graphics.Point
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup RecyclerView with indicator

        val screenWidth = getScreenWidth()
        // One page will occupy 1/3 of screen width
        val recyclerView: RecyclerView = findViewById(R.id.recycler)
        recyclerView.setLayoutManager(
            LinearLayoutManager(
                this,
                LinearLayoutManager.VERTICAL,
                false
            )
        )
        val recyclerAdapter: DemoRecyclerViewAdapter = DemoRecyclerViewAdapter(8, screenWidth / 3)
        recyclerView.setAdapter(recyclerAdapter)

        recyclerView.setPadding(screenWidth / 3, 0, screenWidth / 3, 0)

        val recyclerIndicator: ScrollingPagerIndicator = findViewById(R.id.recycler_indicator)

        // Consider page in the middle current
        recyclerIndicator.attachToRecyclerView(recyclerView)

        val snapHelper: SnapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)


    }

    private fun getScreenWidth(): Int {
        val display = (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay
        val screenSize = Point()
        display.getSize(screenSize)
        return screenSize.x
    }
}