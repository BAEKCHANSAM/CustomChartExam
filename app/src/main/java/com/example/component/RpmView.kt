package com.example.component

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.customchartexam.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class RpmView : View {

  private fun Double.toAngle(): Double = this * (180 / Math.PI)
  private fun Float.toRadian(): Double = this * (Math.PI / 180)

  val Float.dp: Float get() = this * Resources.getSystem().displayMetrics.density
  val Int.dp: Float get() = toFloat().dp

  private var gaugeRadius = 150.dp
  private var remainingGaugeRadius = 158.dp
  private var handleCircleDistance = 170.dp
  private val startAngle = 135f
  private var prevAngle = 135f
  private val maxSweepAngle = 270f
  private val maxRpm = 8031f
  private val arcOffset = 0.5f
  private val gaugeSweepAngle = ((270 - arcOffset * 9) / 10)
  var currentSweepAngle = 0f

  private var isTouchDown = false
  private var animator: ValueAnimator? = null
  private var remainingGaugePath = Path()
  private var gaugePath = Path()
  private var handleArea = 25.dp
  private var handleX = 0f
  private var handleY = 0f
  private var mRpmStateListener: OnRpmStateListener? = null
  var prevStep = 0

  private var remainingGaugePaint = Paint().apply {
    color = ContextCompat.getColor(context, R.color.colorBackgroundFill)
    style = Paint.Style.STROKE
    strokeWidth = 2.dp
  }
  private var gaugePaint = Paint().apply {
    color = ContextCompat.getColor(context, R.color.colorGaugeFill)
    style = Paint.Style.STROKE
    strokeWidth = 18.dp
  }

  private var circlePaint = Paint().apply {
    color = ContextCompat.getColor(context, R.color.colorGaugeFill)
    strokeWidth = 3.dp
  }

  constructor(context: Context) :
    super(context)

  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    initAttrs(attrs)
  }

  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
    context,
    attrs,
    defStyleAttr
  ) {
    initAttrs(attrs)
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    init()
  }

  override fun onDraw(canvas: Canvas?) {
    super.onDraw(canvas)
    canvas?.drawPath(remainingGaugePath, remainingGaugePaint)
    canvas?.drawPath(gaugePath, gaugePaint)
    canvas?.drawCircle(handleX, handleY, 3.dp, circlePaint)
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent?): Boolean {

    when (event?.action ?: return false) {

      MotionEvent.ACTION_DOWN -> {
        val handleArea = getHandleArea()
        if (handleArea.isInside(event.x, event.y)) {
          isTouchDown = true
          mRpmStateListener?.changeHandleTouchState(isTouchDown)
        } else {
          return false
        }
      }

      MotionEvent.ACTION_MOVE -> {
        if (!isTouchDown) {
          return false
        }
        val moveX = event.x
        val moveY = event.y
        val moveRadian = atan2(moveY - height / 2.0, moveX - width / 2.0)
        var moveAngle = moveRadian.toAngle()
        if (moveAngle < 0 || (moveAngle > 0 && moveAngle <= 45)) {
          moveAngle += 360
        }
        var step = ((abs(moveAngle - startAngle) / maxSweepAngle) * 10).toInt()
        if (prevStep < step) {
          step = min(step, 10)
        }
        if (prevStep > step) {
          step = max(step, step + 1)
        }
        if (moveAngle > 45 && moveAngle <= 90) {
          step = 10
        } else if (moveAngle > 90 && moveAngle <= startAngle) {
          step = 0
        }
        prevStep = step
        mRpmStateListener?.onRpmStepChanged(step)
        setGauge(step, 0L)
      }

      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        isTouchDown = false
        mRpmStateListener?.changeHandleTouchState(isTouchDown)
      }
    }
    return true
  }

  private fun initAttrs(attrs: AttributeSet?) {
//    attrs?.also {
//      val typedArray = context.obtainStyledAttributes(it, R.styleable.RpmView)
//      circlePaint.color = typedArray.getColor(
//        R.styleable.RpmView_gaugeColor,
//        ContextCompat.getColor(context, R.color.d_3e98cb_n_76cbef)
//      )
//      gaugePaint.color = circlePaint.color
//      remainingGaugePaint.color = circlePaint.color
//      typedArray.recycle()
//    }
  }

  private fun init() {
    gaugeRadius = width / 2 - handleArea
    remainingGaugeRadius = gaugeRadius + 8.dp
    handleCircleDistance = gaugeRadius + handleArea - 4.dp
    val remainingGaugeRect = getDrawRect(
      width / 2 - remainingGaugeRadius,
      height / 2 - remainingGaugeRadius,
      width / 2 + remainingGaugeRadius,
      height / 2 + remainingGaugeRadius
    )
    remainingGaugePath = Path()
    remainingGaugeRect?.also {
      remainingGaugePath.arcTo(it, startAngle, maxSweepAngle)
    }
    handleX = cos(startAngle.toRadian()).toFloat() * handleCircleDistance + width / 2
    handleY = sin(startAngle.toRadian()).toFloat() * handleCircleDistance + height / 2
  }

  fun setRpm(value: Int) {
    if (value == 0) {
      setGauge(0, 1000L)
    } else {
      setGauge(min(((value / maxRpm) * 10).toInt() + 1, 10), 1000L)
    }

  }

  fun setRpmStep(rpmStep: Int) {
    if (prevStep == rpmStep) {
      return
    }
    post {
      prevStep = rpmStep
      setGauge(rpmStep, 0L)
    }
  }

  private fun setGauge(rpmStep: Int, duration: Long) {
    val rect = getDrawRect(
      width / 2 - gaugeRadius,
      height / 2 - gaugeRadius,
      width / 2 + gaugeRadius,
      height / 2 + gaugeRadius
    ) ?: return
    val remainingGaugeRect = getDrawRect(
      width / 2 - remainingGaugeRadius,
      height / 2 - remainingGaugeRadius,
      width / 2 + remainingGaugeRadius,
      height / 2 + remainingGaugeRadius
    ) ?: return

    gaugePath = Path()
    animator?.cancel()
    val sweepAngle = rpmStep * 27f - (prevAngle - startAngle)
    animator = ValueAnimator.ofFloat(0f, 1f).apply {
      this.duration = duration
      addUpdateListener { valueAnimator ->
        val value = valueAnimator.animatedValue as Float
        currentSweepAngle = sweepAngle * value + (prevAngle - startAngle)
        handleX =
          cos((startAngle + currentSweepAngle).toRadian()).toFloat() * handleCircleDistance + width / 2
        handleY =
          sin((startAngle + currentSweepAngle).toRadian()).toFloat() * handleCircleDistance + height / 2
        setGaugePath(rect, remainingGaugeRect)
        invalidate()
      }

      start()
    }
  }

  private fun setGaugePath(
    gaugeRect: RectF,
    remainingGaugeRect: RectF
  ) {
    gaugePath.reset()
    remainingGaugePath.reset()

    val gaugeCount = ((currentSweepAngle / maxSweepAngle) * 10).toInt()
    var currentAngle = startAngle
    for (i in 0 until gaugeCount) {
      gaugePath.moveTo(
        cos(currentAngle.toRadian()).toFloat() * gaugeRadius + gaugeRect.centerX(),
        sin(currentAngle.toRadian()).toFloat() * gaugeRadius + gaugeRect.centerY()
      )
      gaugePath.arcTo(gaugeRect, currentAngle, gaugeSweepAngle)
      currentAngle += gaugeSweepAngle + arcOffset
    }
    gaugePath.moveTo(
      cos(currentAngle.toRadian()).toFloat() * gaugeRadius + gaugeRect.centerX(),
      sin(currentAngle.toRadian()).toFloat() * gaugeRadius + gaugeRect.centerY()
    )
    val remainingSweepAngle = gaugeSweepAngle * ((currentSweepAngle / maxSweepAngle) * 10 - floor((currentSweepAngle / maxSweepAngle) * 10))
    gaugePath.arcTo(
      gaugeRect,
      currentAngle,
      remainingSweepAngle)

    currentAngle += (remainingSweepAngle - arcOffset)
    remainingGaugePath.moveTo(
      cos(currentAngle.toRadian()).toFloat() * remainingGaugeRadius + remainingGaugeRect.centerX(),
      sin(currentAngle.toRadian()).toFloat() * remainingGaugeRadius + remainingGaugeRect.centerY()
    )
    remainingGaugePath.arcTo(
      remainingGaugeRect,
      currentAngle,
      startAngle + maxSweepAngle - currentAngle
    )
  }

  private fun getDrawRect(left: Float, top: Float, right: Float, bottom: Float): RectF? {
    val rect = RectF()
    rect.left = left + paddingLeft
    rect.right = right - paddingRight
    rect.top = top + paddingTop
    rect.bottom = bottom - paddingBottom
    if (rect.width() <= 0f || rect.height() <= 0f) {
      return null
    }
    return rect
  }

  private fun getHandleArea(): RectF {
    return RectF(
      cos(prevAngle.toRadian()).toFloat() * gaugeRadius + width / 2 - handleArea,
      sin(prevAngle.toRadian()).toFloat() * gaugeRadius + height / 2 - handleArea,
      cos(prevAngle.toRadian()).toFloat() * gaugeRadius + width / 2 + handleArea,
      sin(prevAngle.toRadian()).toFloat() * gaugeRadius + height / 2 + handleArea,
    )
  }

  fun setOnRpmStateListener(onRpmStateListener: OnRpmStateListener?) {
    mRpmStateListener = onRpmStateListener
  }

  private fun RectF.isInside(x: Float, y: Float) =
    x > this.left && x < this.right && y < this.bottom && y > this.top

  interface OnRpmStateListener {
    fun changeHandleTouchState(isTouched: Boolean)
    fun onRpmStepChanged(rpmStep: Int)
  }
}