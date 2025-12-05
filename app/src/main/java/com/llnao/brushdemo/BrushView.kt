package com.llnao.brushdemo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

/**
 * 支持特殊笔刷效果的画图 View
 * 支持蜡笔、毛刷等效果
 */
class BrushView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 笔刷类型枚举
    enum class BrushType {
        NORMAL,     // 普通画笔
        CRAYON,     // 蜡笔
        BRISTLE     // 毛刷（油漆刷）
    }

    // 画布和路径
    private var canvasBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null
    private val canvasPaint = Paint(Paint.DITHER_FLAG)

    // 笔刷属性
    private var brushColor = Color.BLACK
    private var brushSize = 20f
    private var currentBrushType = BrushType.CRAYON

    // 触摸点记录
    private var lastX = 0f
    private var lastY = 0f
    private var currentX = 0f
    private var currentY = 0f

    // 蜡笔效果参数
    private val crayonRandom = Random(System.currentTimeMillis())
    private var crayonTexture: Bitmap? = null

    // 毛刷效果参数
    private val bristleCount = 12  // 刷毛数量
    private val bristleOffsets = FloatArray(bristleCount)
    private val bristlePhases = FloatArray(bristleCount)

    init {
        setupBristles()
    }

    private fun setupBristles() {
        // 初始化刷毛的随机偏移
        for (i in 0 until bristleCount) {
            bristleOffsets[i] = (i - bristleCount / 2f) * 2f
            bristlePhases[i] = Random.nextFloat() * PI.toFloat() * 2
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap!!)
            drawCanvas?.drawColor(Color.WHITE)
            generateCrayonTexture()
        }
    }

    // 生成蜡笔纹理
    private fun generateCrayonTexture() {
        val size = 64
        crayonTexture = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8).apply {
            val canvas = Canvas(this)
            val paint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            // 生成随机噪点纹理
            for (i in 0 until size) {
                for (j in 0 until size) {
                    if (crayonRandom.nextFloat() > 0.3f) {
                        paint.alpha = (crayonRandom.nextFloat() * 200 + 55).toInt()
                        canvas.drawPoint(i.toFloat(), j.toFloat(), paint)
                    }
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvasBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, canvasPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        currentX = event.x
        currentY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = currentX
                lastY = currentY
                drawPoint(currentX, currentY)
            }
            MotionEvent.ACTION_MOVE -> {
                drawStroke(lastX, lastY, currentX, currentY)
                lastX = currentX
                lastY = currentY
            }
            MotionEvent.ACTION_UP -> {
                // 触摸结束
            }
        }

        invalidate()
        return true
    }

    private fun drawPoint(x: Float, y: Float) {
        when (currentBrushType) {
            BrushType.NORMAL -> drawNormalPoint(x, y)
            BrushType.CRAYON -> drawCrayonPoint(x, y)
            BrushType.BRISTLE -> drawBristlePoint(x, y)
        }
    }

    private fun drawStroke(startX: Float, startY: Float, endX: Float, endY: Float) {
        when (currentBrushType) {
            BrushType.NORMAL -> drawNormalStroke(startX, startY, endX, endY)
            BrushType.CRAYON -> drawCrayonStroke(startX, startY, endX, endY)
            BrushType.BRISTLE -> drawBristleStroke(startX, startY, endX, endY)
        }
    }

    // ==================== 普通画笔 ====================

    private fun drawNormalPoint(x: Float, y: Float) {
        val paint = Paint().apply {
            color = brushColor
            strokeWidth = brushSize
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        drawCanvas?.drawCircle(x, y, brushSize / 2, paint)
    }

    private fun drawNormalStroke(startX: Float, startY: Float, endX: Float, endY: Float) {
        val paint = Paint().apply {
            color = brushColor
            strokeWidth = brushSize
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        drawCanvas?.drawLine(startX, startY, endX, endY, paint)
    }

    // ==================== 蜡笔效果 ====================

    private fun drawCrayonPoint(x: Float, y: Float) {
        drawCrayonDab(x, y, brushSize)
    }

    private fun drawCrayonStroke(startX: Float, startY: Float, endX: Float, endY: Float) {
        val dx = endX - startX
        val dy = endY - startY
        val distance = sqrt(dx * dx + dy * dy)
        
        // 根据距离插值绘制多个蜡笔点
        val steps = max(1, (distance / (brushSize * 0.2f)).toInt())
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = startX + dx * t
            val y = startY + dy * t
            drawCrayonDab(x, y, brushSize)
        }
    }

    private fun drawCrayonDab(centerX: Float, centerY: Float, size: Float) {
        val canvas = drawCanvas ?: return
        val paint = Paint().apply {
            color = brushColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val radius = size / 2
        val particleCount = (size * 3).toInt()

        // 绘制主体颗粒效果
        for (i in 0 until particleCount) {
            // 随机位置，使用高斯分布让中心更密集
            val angle = crayonRandom.nextFloat() * 2 * PI.toFloat()
            val r = radius * sqrt(crayonRandom.nextFloat()) * (0.8f + crayonRandom.nextFloat() * 0.4f)
            
            val px = centerX + cos(angle) * r
            val py = centerY + sin(angle) * r

            // 随机大小的颗粒
            val particleSize = crayonRandom.nextFloat() * 3f + 1f
            
            // 随机透明度，模拟蜡笔不均匀着色
            paint.alpha = (crayonRandom.nextFloat() * 100 + 100).toInt()
            
            canvas.drawCircle(px, py, particleSize, paint)
        }

        // 添加边缘的粗糙感
        val edgeParticles = (size * 1.5f).toInt()
        for (i in 0 until edgeParticles) {
            val angle = crayonRandom.nextFloat() * 2 * PI.toFloat()
            val r = radius * (0.7f + crayonRandom.nextFloat() * 0.5f)
            
            val px = centerX + cos(angle) * r
            val py = centerY + sin(angle) * r
            
            paint.alpha = (crayonRandom.nextFloat() * 80 + 40).toInt()
            val particleSize = crayonRandom.nextFloat() * 2f + 0.5f
            
            canvas.drawCircle(px, py, particleSize, paint)
        }

        // 模拟蜡笔的纹理条纹
        val stripeCount = (size / 3).toInt()
        for (i in 0 until stripeCount) {
            val offsetY = (i - stripeCount / 2f) * 2f + crayonRandom.nextFloat() * 2f
            val startX = centerX - radius * 0.8f
            val endX = centerX + radius * 0.8f
            
            paint.alpha = (crayonRandom.nextFloat() * 60 + 30).toInt()
            paint.strokeWidth = crayonRandom.nextFloat() * 2f + 1f
            
            // 绘制不规则的条纹
            val path = Path()
            path.moveTo(startX, centerY + offsetY)
            var cx = startX
            while (cx < endX) {
                cx += crayonRandom.nextFloat() * 8f + 4f
                val cy = centerY + offsetY + (crayonRandom.nextFloat() - 0.5f) * 3f
                path.lineTo(min(cx, endX), cy)
            }
            
            paint.style = Paint.Style.STROKE
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
        }
    }

    // ==================== 毛刷效果（油漆刷） ====================

    private fun drawBristlePoint(x: Float, y: Float) {
        drawBristleDab(x, y, 0f)
    }

    private fun drawBristleStroke(startX: Float, startY: Float, endX: Float, endY: Float) {
        val dx = endX - startX
        val dy = endY - startY
        val distance = sqrt(dx * dx + dy * dy)
        val angle = atan2(dy, dx)

        // 更密集的插值以实现平滑效果
        val steps = max(1, (distance / 2f).toInt())
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = startX + dx * t
            val y = startY + dy * t
            drawBristleDab(x, y, angle)
        }
    }

    private fun drawBristleDab(centerX: Float, centerY: Float, strokeAngle: Float) {
        val canvas = drawCanvas ?: return
        
        // 刷毛垂直于笔画方向
        val perpAngle = strokeAngle + PI.toFloat() / 2
        val bristleSpacing = brushSize / bristleCount

        for (i in 0 until bristleCount) {
            val paint = Paint().apply {
                color = brushColor
                strokeWidth = 2f + Random.nextFloat() * 2f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
                // 每根刷毛略有透明度变化
                alpha = (180 + Random.nextInt(75))
            }

            // 计算刷毛位置
            val offset = bristleOffsets[i] * bristleSpacing
            val baseX = centerX + cos(perpAngle) * offset
            val baseY = centerY + sin(perpAngle) * offset

            // 刷毛的随机抖动
            val jitterX = (Random.nextFloat() - 0.5f) * 3f
            val jitterY = (Random.nextFloat() - 0.5f) * 3f

            val x = baseX + jitterX
            val y = baseY + jitterY

            // 绘制刷毛痕迹（短线段）
            val bristleLength = brushSize * 0.3f + Random.nextFloat() * brushSize * 0.2f
            val endX = x + cos(strokeAngle) * bristleLength
            val endY = y + sin(strokeAngle) * bristleLength

            canvas.drawLine(x, y, endX, endY, paint)

            // 添加刷毛的细节点
            if (Random.nextFloat() > 0.5f) {
                paint.style = Paint.Style.FILL
                paint.alpha = (100 + Random.nextInt(100))
                canvas.drawCircle(x, y, Random.nextFloat() * 2f + 1f, paint)
            }
        }

        // 添加油漆的湿润感 - 在边缘添加一些小水滴
        if (Random.nextFloat() > 0.7f) {
            val paint = Paint().apply {
                color = brushColor
                alpha = (60 + Random.nextInt(60))
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            val dropOffset = brushSize / 2 + Random.nextFloat() * 5f
            val dropAngle = perpAngle + (Random.nextFloat() - 0.5f) * 0.5f
            val dropX = centerX + cos(dropAngle) * dropOffset * (if (Random.nextBoolean()) 1 else -1)
            val dropY = centerY + sin(dropAngle) * dropOffset * (if (Random.nextBoolean()) 1 else -1)
            
            canvas.drawCircle(dropX, dropY, Random.nextFloat() * 2f + 1f, paint)
        }
    }

    // ==================== 公共方法 ====================

    /**
     * 设置笔刷类型
     */
    fun setBrushType(type: BrushType) {
        currentBrushType = type
    }

    /**
     * 获取当前笔刷类型
     */
    fun getBrushType(): BrushType = currentBrushType

    /**
     * 设置笔刷颜色
     */
    fun setBrushColor(color: Int) {
        brushColor = color
    }

    /**
     * 获取笔刷颜色
     */
    fun getBrushColor(): Int = brushColor

    /**
     * 设置笔刷大小
     */
    fun setBrushSize(size: Float) {
        brushSize = size.coerceIn(5f, 100f)
    }

    /**
     * 获取笔刷大小
     */
    fun getBrushSize(): Float = brushSize

    /**
     * 清空画布
     */
    fun clear() {
        drawCanvas?.drawColor(Color.WHITE)
        invalidate()
    }

    /**
     * 撤销（需要实现历史记录功能）
     */
    fun undo() {
        // TODO: 实现撤销功能
    }

    /**
     * 获取当前画布的 Bitmap
     */
    fun getBitmap(): Bitmap? = canvasBitmap?.copy(Bitmap.Config.ARGB_8888, false)

    /**
     * 设置背景颜色
     */
    fun setCanvasColor(color: Int) {
        drawCanvas?.drawColor(color)
        invalidate()
    }
}
