package com.llnao.brushdemo

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var brushView: BrushView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViews()
    }

    private fun setupViews() {
        brushView = findViewById(R.id.brushView)

        // 笔刷类型按钮
        findViewById<Button>(R.id.btnNormal).setOnClickListener {
            brushView.setBrushType(BrushView.BrushType.NORMAL)
            updateBrushTypeButtons(BrushView.BrushType.NORMAL)
        }

        findViewById<Button>(R.id.btnCrayon).setOnClickListener {
            brushView.setBrushType(BrushView.BrushType.CRAYON)
            updateBrushTypeButtons(BrushView.BrushType.CRAYON)
        }

        findViewById<Button>(R.id.btnBristle).setOnClickListener {
            brushView.setBrushType(BrushView.BrushType.BRISTLE)
            updateBrushTypeButtons(BrushView.BrushType.BRISTLE)
        }

        // 清空按钮
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            brushView.clear()
        }

        // 颜色选择
        findViewById<View>(R.id.colorBlack).setOnClickListener {
            brushView.setBrushColor(Color.BLACK)
        }
        findViewById<View>(R.id.colorRed).setOnClickListener {
            brushView.setBrushColor(Color.parseColor("#E53935"))
        }
        findViewById<View>(R.id.colorBlue).setOnClickListener {
            brushView.setBrushColor(Color.parseColor("#1E88E5"))
        }
        findViewById<View>(R.id.colorGreen).setOnClickListener {
            brushView.setBrushColor(Color.parseColor("#43A047"))
        }
        findViewById<View>(R.id.colorYellow).setOnClickListener {
            brushView.setBrushColor(Color.parseColor("#FDD835"))
        }
        findViewById<View>(R.id.colorOrange).setOnClickListener {
            brushView.setBrushColor(Color.parseColor("#FB8C00"))
        }

        // 笔刷大小
        findViewById<SeekBar>(R.id.sizeSeekBar).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    // 大小范围 5 - 100
                    brushView.setBrushSize((progress + 5).toFloat())
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 默认选中蜡笔
        updateBrushTypeButtons(BrushView.BrushType.CRAYON)
    }

    private fun updateBrushTypeButtons(selectedType: BrushView.BrushType) {
        val btnNormal = findViewById<Button>(R.id.btnNormal)
        val btnCrayon = findViewById<Button>(R.id.btnCrayon)
        val btnBristle = findViewById<Button>(R.id.btnBristle)

        // 重置所有按钮样式
        listOf(btnNormal, btnCrayon, btnBristle).forEach { btn ->
            btn.alpha = 0.6f
        }

        // 高亮选中的按钮
        when (selectedType) {
            BrushView.BrushType.NORMAL -> btnNormal.alpha = 1.0f
            BrushView.BrushType.CRAYON -> btnCrayon.alpha = 1.0f
            BrushView.BrushType.BRISTLE -> btnBristle.alpha = 1.0f
        }
    }
}
