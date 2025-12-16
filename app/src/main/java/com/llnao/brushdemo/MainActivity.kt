package com.llnao.brushdemo

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var brushView: BrushView
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private lateinit var sizeSeekBar: SeekBar
    private lateinit var labelSize: TextView

    // 记住画笔模式下的大小
    private var savedBrushSize = 28
    private var savedEraserSize = 40

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        sizeSeekBar = findViewById(R.id.sizeSeekBar)
        labelSize = findViewById(R.id.labelSize)

        // 设置 Undo/Redo 状态监听
        brushView.onHistoryChangedListener = { canUndo, canRedo ->
            btnUndo.isEnabled = canUndo
            btnUndo.alpha = if (canUndo) 1.0f else 0.4f
            btnRedo.isEnabled = canRedo
            btnRedo.alpha = if (canRedo) 1.0f else 0.4f
        }

        // 初始化 Undo/Redo 按钮状态
        btnUndo.isEnabled = false
        btnUndo.alpha = 0.4f
        btnRedo.isEnabled = false
        btnRedo.alpha = 0.4f

        // 笔刷类型按钮
        findViewById<Button>(R.id.btnNormal).setOnClickListener {
            switchToBrushMode(BrushView.BrushType.NORMAL)
        }

        findViewById<Button>(R.id.btnCrayon).setOnClickListener {
            switchToBrushMode(BrushView.BrushType.CRAYON)
        }

        findViewById<Button>(R.id.btnBristle).setOnClickListener {
            switchToBrushMode(BrushView.BrushType.BRISTLE)
        }

        findViewById<Button>(R.id.btnEraser).setOnClickListener {
            switchToEraserMode()
        }

        // Undo/Redo 按钮
        btnUndo.setOnClickListener {
            brushView.undo()
        }

        btnRedo.setOnClickListener {
            brushView.redo()
        }

        // 清空按钮
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            brushView.clear()
        }

        // 保存按钮
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveImage()
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

        // 笔刷/橡皮擦大小
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (brushView.getBrushType() == BrushView.BrushType.ERASER) {
                    // 橡皮擦大小范围 10 - 150
                    val size = progress + 10
                    brushView.setEraserSize(size.toFloat())
                    savedEraserSize = progress
                } else {
                    // 画笔大小范围 5 - 100
                    val size = progress + 5
                    brushView.setBrushSize(size.toFloat())
                    savedBrushSize = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 默认选中蜡笔
        updateBrushTypeButtons(BrushView.BrushType.CRAYON)
    }

    /**
     * 保存图片到相册
     */
    private fun saveImage() {
        // 检查涂抹覆盖率
        val coverageRate = brushView.getDrawnCoverageRate()
        val coveragePercent = (coverageRate * 100).toInt()

        if (coverageRate < 0.6f) {
            // 覆盖率不足60%，显示提示
            val message = "当前涂抹覆盖率仅为 ${coveragePercent}%，建议至少达到 60% 再保存。\n是否继续保存？"
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage(message)
                .setPositiveButton("继续保存") { _, _ ->
                    performSaveImage()
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        // 覆盖率达标，直接保存
        performSaveImage()
    }

    /**
     * 执行保存图片操作
     */
    private fun performSaveImage() {
        val bitmap = brushView.getBitmap()
        if (bitmap == null) {
            Toast.makeText(this, "获取图片失败", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = "BrushDemo_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
            var outputStream: OutputStream? = null
            var savedPath = ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/BrushDemo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    outputStream = contentResolver.openOutputStream(uri)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream!!)
                    outputStream.close()

                    // 标记保存完成
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)

                    savedPath = "Pictures/BrushDemo/$fileName"
                }
            } else {
                // Android 9 及以下使用传统方式
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "BrushDemo")
                if (!appDir.exists()) {
                    appDir.mkdirs()
                }

                val file = File(appDir, fileName)
                outputStream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.close()

                // 通知媒体库更新
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                savedPath = file.absolutePath
            }

            bitmap.recycle()
            Toast.makeText(this, "图片已保存到: $savedPath", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchToBrushMode(type: BrushView.BrushType) {
        brushView.setBrushType(type)
        updateBrushTypeButtons(type)
        
        // 切换回画笔模式时恢复画笔大小
        labelSize.text = "大小："
        sizeSeekBar.max = 95
        sizeSeekBar.progress = savedBrushSize
        brushView.setBrushSize((savedBrushSize + 5).toFloat())
    }

    private fun switchToEraserMode() {
        brushView.setBrushType(BrushView.BrushType.ERASER)
        updateBrushTypeButtons(BrushView.BrushType.ERASER)
        
        // 切换到橡皮擦模式时使用橡皮擦大小
        labelSize.text = "橡皮："
        sizeSeekBar.max = 140
        sizeSeekBar.progress = savedEraserSize
        brushView.setEraserSize((savedEraserSize + 10).toFloat())
    }

    private fun updateBrushTypeButtons(selectedType: BrushView.BrushType) {
        val btnNormal = findViewById<Button>(R.id.btnNormal)
        val btnCrayon = findViewById<Button>(R.id.btnCrayon)
        val btnBristle = findViewById<Button>(R.id.btnBristle)
        val btnEraser = findViewById<Button>(R.id.btnEraser)

        // 重置所有按钮样式
        listOf(btnNormal, btnCrayon, btnBristle, btnEraser).forEach { btn ->
            btn.alpha = 0.6f
        }

        // 高亮选中的按钮
        when (selectedType) {
            BrushView.BrushType.NORMAL -> btnNormal.alpha = 1.0f
            BrushView.BrushType.CRAYON -> btnCrayon.alpha = 1.0f
            BrushView.BrushType.BRISTLE -> btnBristle.alpha = 1.0f
            BrushView.BrushType.ERASER -> btnEraser.alpha = 1.0f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        brushView.release()
    }
}
