package com.llnao.brushdemo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*
import kotlin.random.Random

/**
 * 支持特殊笔刷效果的圆形画图 View
 * 支持蜡笔、毛刷、橡皮擦等效果，以及 Undo/Redo 功能
 * 只能在圆形区域内绘画
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
        BRISTLE,    // 毛刷（油漆刷）
        ERASER      // 橡皮擦
    }

    // 画布和路径
    private var canvasBitmap: Bitmap? = null
    private var drawCanvas: Canvas? = null
    private val canvasPaint = Paint(Paint.DITHER_FLAG)

    // 圆形画布相关
    private var centerX = 0f
    private var centerY = 0f
    private var circleRadius = 0f
    private val circlePath = Path()
    private val borderPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val outsidePaint = Paint().apply {
        color = Color.parseColor("#F0F0F0")  // 圆外的背景色
        style = Paint.Style.FILL
    }

    // 笔刷属性
    private var brushColor = Color.BLACK
    private var brushSize = 20f
    private var currentBrushType = BrushType.CRAYON

    // 橡皮擦大小（可以独立设置）
    private var eraserSize = 40f

    // 触摸点记录
    private var lastX = 0f
    private var lastY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDrawing = false  // 是否正在绘制（触摸点在圆内）
    private var isFirstMove = true  // 是否是第一次移动（用于延迟绘制起始点，避免方向错误）

    // 蜡笔效果参数
    private val crayonRandom = Random(System.currentTimeMillis())
    private var crayonTexture: Bitmap? = null

    // 毛刷效果参数
    private val bristleCount = 12  // 刷毛数量
    private val bristleOffsets = FloatArray(bristleCount)
    private val bristlePhases = FloatArray(bristleCount)

    // ==================== Undo/Redo 历史记录 ====================
    private val undoStack = mutableListOf<Bitmap>()
    private val redoStack = mutableListOf<Bitmap>()
    private val maxHistorySize = 20  // 最大历史记录数量

    // 状态变化监听器
    var onHistoryChangedListener: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

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
            // 计算圆形区域（居中，取宽高的较小值作为直径，留出边距）
            val padding = 20f
            centerX = w / 2f
            centerY = h / 2f
            circleRadius = (min(w, h) / 2f) - padding
            
            // 创建圆形裁剪路径
            circlePath.reset()
            circlePath.addCircle(centerX, centerY, circleRadius, Path.Direction.CW)
            
            // 创建画布
            canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawCanvas = Canvas(canvasBitmap!!)
            
            // 用白色填充圆形区域
            clearCircleArea()
            
            generateCrayonTexture()
            
            // 初始化时清空历史记录
            undoStack.clear()
            redoStack.clear()
            notifyHistoryChanged()
        }
    }

    /**
     * 清空圆形区域（只清空圆内）
     */
    private fun clearCircleArea() {
        drawCanvas?.let { canvas ->
            // 先用透明色清空整个画布
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            
            // 只在圆形区域内填充白色
            val paint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(centerX, centerY, circleRadius, paint)
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
        
        // 绘制圆外的背景
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), outsidePaint)
        
        // 裁剪成圆形区域并绘制画布内容
        canvas.save()
        canvas.clipPath(circlePath)
        canvasBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, canvasPaint)
        }
        canvas.restore()
        
        // 绘制圆形边框
        canvas.drawCircle(centerX, centerY, circleRadius, borderPaint)
    }

    /**
     * 检查点是否在圆形区域内
     */
    private fun isInsideCircle(x: Float, y: Float): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        return (dx * dx + dy * dy) <= (circleRadius * circleRadius)
    }

    /**
     * 将点限制在圆形区域内（用于边缘绘制）
     */
    private fun clampToCircle(x: Float, y: Float): Pair<Float, Float> {
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)
        
        return if (distance <= circleRadius) {
            Pair(x, y)
        } else {
            // 将点移到圆边上
            val ratio = circleRadius / distance
            Pair(centerX + dx * ratio, centerY + dy * ratio)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        currentX = event.x
        currentY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 检查触摸点是否在圆形区域内
                if (isInsideCircle(currentX, currentY)) {
                    isDrawing = true
                    isFirstMove = true  // 标记还没有移动过
                    
                    // 在开始新笔画前保存当前状态
                    saveToUndoStack()
                    
                    lastX = currentX
                    lastY = currentY
                    
                    // 不在这里立即绘制点，而是等到 ACTION_MOVE 时绘制
                    // 这样可以获取正确的笔画方向，避免毛刷等方向敏感笔刷出现"小尾巴"
                    // 如果只是点击（没有移动），会在 ACTION_UP 时绘制
                } else {
                    isDrawing = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    // 计算目标点（在圆内或限制到圆边上）
                    val (targetX, targetY) = if (isInsideCircle(currentX, currentY)) {
                        Pair(currentX, currentY)
                    } else {
                        clampToCircle(currentX, currentY)
                    }
                    
                    // 如果是第一次移动，并且移动距离足够，绘制从起始点到当前点的笔画
                    if (isFirstMove) {
                        val dx = targetX - lastX
                        val dy = targetY - lastY
                        val distance = sqrt(dx * dx + dy * dy)
                        
                        // 移动距离足够时才认为是真正的移动（避免手指抖动）
                        if (distance > 2f) {
                            isFirstMove = false
                            drawStroke(lastX, lastY, targetX, targetY)
                        }
                    } else {
                        drawStroke(lastX, lastY, targetX, targetY)
                    }
                    
                    lastX = targetX
                    lastY = targetY
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                // 如果只是点击（没有移动），在这里绘制一个点
                if (isDrawing && isFirstMove) {
                    drawPointWithoutDirection(lastX, lastY)
                    invalidate()
                }
                isDrawing = false
                isFirstMove = true
            }
        }

        return true
    }

    // ==================== 历史记录管理 ====================

    private fun saveToUndoStack() {
        canvasBitmap?.let { bitmap ->
            // 复制当前画布状态
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            undoStack.add(copy)
            
            // 限制历史记录大小
            while (undoStack.size > maxHistorySize) {
                undoStack.removeAt(0).recycle()
            }
            
            // 新操作会清空 redo 栈
            redoStack.forEach { it.recycle() }
            redoStack.clear()
            
            notifyHistoryChanged()
        }
    }

    private fun notifyHistoryChanged() {
        onHistoryChangedListener?.invoke(canUndo(), canRedo())
    }

    /**
     * 撤销上一步操作
     */
    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        
        // 将当前状态保存到 redo 栈
        canvasBitmap?.let { bitmap ->
            redoStack.add(bitmap.copy(Bitmap.Config.ARGB_8888, true))
        }
        
        // 恢复上一个状态
        val previousState = undoStack.removeAt(undoStack.size - 1)
        canvasBitmap = previousState
        drawCanvas = Canvas(canvasBitmap!!)
        
        invalidate()
        notifyHistoryChanged()
        return true
    }

    /**
     * 重做上一步撤销的操作
     */
    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        
        // 将当前状态保存到 undo 栈
        canvasBitmap?.let { bitmap ->
            undoStack.add(bitmap.copy(Bitmap.Config.ARGB_8888, true))
        }
        
        // 恢复下一个状态
        val nextState = redoStack.removeAt(redoStack.size - 1)
        canvasBitmap = nextState
        drawCanvas = Canvas(canvasBitmap!!)
        
        invalidate()
        notifyHistoryChanged()
        return true
    }

    /**
     * 是否可以撤销
     */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /**
     * 是否可以重做
     */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // ==================== 绘制入口 ====================

    /**
     * 绘制一个无方向的点（用于只点击不拖动的情况）
     * 对于毛刷等方向敏感的笔刷，绘制一个圆形而不是带方向的刷痕
     */
    private fun drawPointWithoutDirection(x: Float, y: Float) {
        // 只在圆内绘制
        if (!isInsideCircle(x, y)) return
        
        when (currentBrushType) {
            BrushType.NORMAL -> drawNormalPoint(x, y)
            BrushType.CRAYON -> drawCrayonPoint(x, y)
            BrushType.BRISTLE -> drawBristlePointWithoutDirection(x, y)  // 使用无方向版本
            BrushType.ERASER -> drawEraserPoint(x, y)
        }
    }

    private fun drawPoint(x: Float, y: Float) {
        // 只在圆内绘制
        if (!isInsideCircle(x, y)) return
        
        when (currentBrushType) {
            BrushType.NORMAL -> drawNormalPoint(x, y)
            BrushType.CRAYON -> drawCrayonPoint(x, y)
            BrushType.BRISTLE -> drawBristlePoint(x, y)
            BrushType.ERASER -> drawEraserPoint(x, y)
        }
    }

    private fun drawStroke(startX: Float, startY: Float, endX: Float, endY: Float) {
        when (currentBrushType) {
            BrushType.NORMAL -> drawNormalStroke(startX, startY, endX, endY)
            BrushType.CRAYON -> drawCrayonStroke(startX, startY, endX, endY)
            BrushType.BRISTLE -> drawBristleStroke(startX, startY, endX, endY)
            BrushType.ERASER -> drawEraserStroke(startX, startY, endX, endY)
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
        drawCanvas?.save()
        drawCanvas?.clipPath(circlePath)
        drawCanvas?.drawCircle(x, y, brushSize / 2, paint)
        drawCanvas?.restore()
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
        drawCanvas?.save()
        drawCanvas?.clipPath(circlePath)
        drawCanvas?.drawLine(startX, startY, endX, endY, paint)
        drawCanvas?.restore()
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
        
        // 裁剪到圆形区域
        canvas.save()
        canvas.clipPath(circlePath)
        
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
        
        canvas.restore()
    }

    // ==================== 毛刷效果（油漆刷） ====================

    private fun drawBristlePoint(x: Float, y: Float) {
        drawBristleDab(x, y, 0f)
    }

    /**
     * 绘制无方向的毛刷点（用于只点击不拖动的情况）
     * 绘制一个圆形散布的点，而不是带方向的刷痕
     */
    private fun drawBristlePointWithoutDirection(x: Float, y: Float) {
        val canvas = drawCanvas ?: return
        
        canvas.save()
        canvas.clipPath(circlePath)
        
        val paint = Paint().apply {
            color = brushColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // 绘制圆形散布的点，模拟毛刷轻点的效果
        val radius = brushSize / 2
        for (i in 0 until bristleCount * 2) {
            val angle = Random.nextFloat() * 2 * PI.toFloat()
            val r = radius * sqrt(Random.nextFloat())
            
            val px = x + cos(angle) * r
            val py = y + sin(angle) * r
            
            paint.alpha = (150 + Random.nextInt(105))
            val dotSize = 1.5f + Random.nextFloat() * 2f
            
            canvas.drawCircle(px, py, dotSize, paint)
        }
        
        canvas.restore()
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
        
        // 裁剪到圆形区域
        canvas.save()
        canvas.clipPath(circlePath)
        
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
        
        canvas.restore()
    }

    // ==================== 橡皮擦效果 ====================

    private fun drawEraserPoint(x: Float, y: Float) {
        val paint = Paint().apply {
            color = Color.WHITE
            strokeWidth = eraserSize
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        drawCanvas?.save()
        drawCanvas?.clipPath(circlePath)
        drawCanvas?.drawCircle(x, y, eraserSize / 2, paint)
        drawCanvas?.restore()
    }

    private fun drawEraserStroke(startX: Float, startY: Float, endX: Float, endY: Float) {
        val paint = Paint().apply {
            color = Color.WHITE
            strokeWidth = eraserSize
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        drawCanvas?.save()
        drawCanvas?.clipPath(circlePath)
        drawCanvas?.drawLine(startX, startY, endX, endY, paint)
        drawCanvas?.restore()
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
     * 设置橡皮擦大小
     */
    fun setEraserSize(size: Float) {
        eraserSize = size.coerceIn(10f, 150f)
    }

    /**
     * 获取橡皮擦大小
     */
    fun getEraserSize(): Float = eraserSize

    /**
     * 清空画布（只清空圆形区域内）
     */
    fun clear() {
        // 保存当前状态到 undo 栈
        saveToUndoStack()
        
        clearCircleArea()
        invalidate()
    }

    /**
     * 获取当前画布的 Bitmap（圆形区域）
     */
    fun getBitmap(): Bitmap? {
        val bitmap = canvasBitmap?.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        
        // 创建圆形遮罩的输出
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = Paint().apply {
            isAntiAlias = true
        }
        
        // 绘制圆形区域
        canvas.drawCircle(centerX, centerY, circleRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        bitmap.recycle()
        return output
    }

    /**
     * 设置背景颜色（圆形区域内）
     */
    fun setCanvasColor(color: Int) {
        drawCanvas?.save()
        drawCanvas?.clipPath(circlePath)
        drawCanvas?.drawColor(color)
        drawCanvas?.restore()
        invalidate()
    }

    /**
     * 设置圆形边框颜色
     */
    fun setBorderColor(color: Int) {
        borderPaint.color = color
        invalidate()
    }

    /**
     * 设置圆形边框宽度
     */
    fun setBorderWidth(width: Float) {
        borderPaint.strokeWidth = width
        invalidate()
    }

    /**
     * 获取圆形半径
     */
    fun getCircleRadius(): Float = circleRadius

    /**
     * 获取圆心坐标
     */
    fun getCircleCenter(): Pair<Float, Float> = Pair(centerX, centerY)

    /**
     * 释放资源
     */
    fun release() {
        undoStack.forEach { it.recycle() }
        undoStack.clear()
        redoStack.forEach { it.recycle() }
        redoStack.clear()
        crayonTexture?.recycle()
        canvasBitmap?.recycle()
    }
}
