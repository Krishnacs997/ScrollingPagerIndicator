package com.example.doottest;

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.IntDef
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.min

/**
 * @author Krishna chaudhary
 */
class ScrollingPagerIndicator @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.scrollingPagerIndicatorStyle) :
    View(context, attrs, defStyleAttr) {
    @IntDef(*[RecyclerView.HORIZONTAL, RecyclerView.VERTICAL])
    annotation class Orientation

    private var infiniteDotCount = 0

    private val dotMinimumSize: Int
    private val dotNormalSize: Int
    private val dotSelectedSize: Int
    private val spaceBetweenDotCenters: Int
    private var visibleDotCount = 0
    private var visibleDotThreshold: Int
    private var orientation: Int

    private var visibleFramePosition = 0f
    private var visibleFrameWidth = 0f

    private var firstDotOffset = 0f
    private var dotScale: SparseArray<Float>? = null

    private var itemCount = 0

    private val paint: Paint
    private val colorEvaluator = ArgbEvaluator()

    private var dotColor: Int

    private var selectedDotColor: Int

    private val firstDotDrawable: Drawable?

    private val lastDotDrawable: Drawable?

    private var looped: Boolean

    private var attachRunnable: Runnable? = null
    private var currentAttacher: PagerAttacher<*>? = null
    private var autoRtl = true

    private var dotCountInitialized = false

    init {
        val attributes = context.obtainStyledAttributes(
            attrs,
            R.styleable.ScrollingPagerIndicator,
            defStyleAttr,
            R.style.ScrollingPagerIndicator
        )
        dotColor = attributes.getColor(R.styleable.ScrollingPagerIndicator_spi_dotColor, 0)
        selectedDotColor =
            attributes.getColor(R.styleable.ScrollingPagerIndicator_spi_dotSelectedColor, dotColor)
        dotNormalSize =
            attributes.getDimensionPixelSize(R.styleable.ScrollingPagerIndicator_spi_dotSize, 0)
        dotSelectedSize = attributes.getDimensionPixelSize(
            R.styleable.ScrollingPagerIndicator_spi_dotSelectedSize,
            0
        )
        val dotMinimumSize = attributes.getDimensionPixelSize(
            R.styleable.ScrollingPagerIndicator_spi_dotMinimumSize,
            -1
        )
        this.dotMinimumSize = if (dotMinimumSize <= dotNormalSize) dotMinimumSize else -1

        spaceBetweenDotCenters = attributes.getDimensionPixelSize(
            R.styleable.ScrollingPagerIndicator_spi_dotSpacing,
            0
        ) + dotNormalSize
        looped = attributes.getBoolean(R.styleable.ScrollingPagerIndicator_spi_looped, false)
        val visibleDotCount =
            attributes.getInt(R.styleable.ScrollingPagerIndicator_spi_visibleDotCount, 0)
        setVisibleDotCount(visibleDotCount)
        visibleDotThreshold =
            attributes.getInt(R.styleable.ScrollingPagerIndicator_spi_visibleDotThreshold, 2)
        orientation = attributes.getInt(
            R.styleable.ScrollingPagerIndicator_spi_orientation,
            RecyclerView.HORIZONTAL
        )

        firstDotDrawable =
            attributes.getDrawable(R.styleable.ScrollingPagerIndicator_spi_firstDotDrawable)
        lastDotDrawable =
            attributes.getDrawable(R.styleable.ScrollingPagerIndicator_spi_lastDotDrawable)
        attributes.recycle()

        paint = Paint()
        paint.isAntiAlias = true

        if (isInEditMode) {
            setDotCount(visibleDotCount)
            onPageScrolled(visibleDotCount / 2, 0f)
        }
    }

    /**
     * You should make indicator looped in your PagerAttacher implementation if your custom pager is looped too
     * If pager has less items than visible_dot_count, indicator will work as usual;
     * otherwise it will always be in infinite state.
     *
     * @param looped true if pager is looped
     */
    fun setLooped(looped: Boolean) {
        this.looped = looped
        reattach()
        invalidate()
    }

    /**
     * @return not selected dot color
     */
    @ColorInt
    fun getDotColor(): Int {
        return dotColor
    }

    /**
     * Sets dot color
     *
     * @param color dot color
     */
    fun setDotColor(@ColorInt color: Int) {
        this.dotColor = color
        invalidate()
    }

    /**
     * @return the selected dot color
     */
    @ColorInt
    fun getSelectedDotColor(): Int {
        return selectedDotColor
    }

    /**
     * Sets selected dot color
     *
     * @param color selected dot color
     */
    fun setSelectedDotColor(@ColorInt color: Int) {
        this.selectedDotColor = color
        invalidate()
    }

    /**
     * Maximum number of dots which will be visible at the same time.
     * If pager has more pages than visible_dot_count, indicator will scroll to show extra dots.
     * Must be odd number.
     *
     * @return visible dot count
     */
    fun getVisibleDotCount(): Int {
        return visibleDotCount
    }

    /**
     * Sets visible dot count. Maximum number of dots which will be visible at the same time.
     * If pager has more pages than visible_dot_count, indicator will scroll to show extra dots.
     * Must be odd number.
     *
     * @param visibleDotCount visible dot count
     * @throws IllegalStateException when pager is already attached
     */
    private fun setVisibleDotCount(visibleDotCount: Int) {
        require(visibleDotCount % 2 != 0) { "visibleDotCount must be odd" }
        this.visibleDotCount = visibleDotCount
        this.infiniteDotCount = visibleDotCount + 2

        if (attachRunnable != null) {
            reattach()
        } else {
            requestLayout()
        }
    }

    /**
     * The minimum number of dots which should be visible.
     * If pager has less pages than visibleDotThreshold, no dots will be shown.
     *
     * @return visible dot threshold.
     */
    fun getVisibleDotThreshold(): Int {
        return visibleDotThreshold
    }

    /**
     * Sets the minimum number of dots which should be visible.
     * If pager has less pages than visibleDotThreshold, no dots will be shown.
     *
     * @param visibleDotThreshold visible dot threshold.
     */
    fun setVisibleDotThreshold(visibleDotThreshold: Int) {
        this.visibleDotThreshold = visibleDotThreshold
        if (attachRunnable != null) {
            reattach()
        } else {
            requestLayout()
        }
    }

    /**
     * The visible orientation of the dots
     *
     * @return dot orientation (RecyclerView.HORIZONTAL, RecyclerView.VERTICAL)
     */
    @Orientation
    fun getOrientation(): Int {
        return orientation
    }

    /**
     * Set the dot orientation
     *
     * @param orientation dot orientation (RecyclerView.HORIZONTAL, RecyclerView.VERTICAL)
     */
    fun setOrientation(@Orientation orientation: Int) {
        this.orientation = orientation
        if (attachRunnable != null) {
            reattach()
        } else {
            requestLayout()
        }
    }

    /**
     * Attaches indicator to ViewPager
     *
     * @param pager pager to attach
     */
    /* public void attachToPager(@NonNull ViewPager pager) {
        attachToPager(pager, new ViewPagerAttacher());
    }*/
    /**
     * Attaches indicator to ViewPager2
     *
     * @param pager pager to attach
     */
    /*  public void attachToPager(@NonNull ViewPager2 pager) {
        attachToPager(pager, new ViewPager2Attacher());
    }
*/
    /**
     * Attaches indicator to RecyclerView. Use this method if current page of the recycler is centered.
     * All pages must have the same width.
     * Like this:
     *
     *
     * +------------------------------+
     * |---+  +----------------+  +---|
     * |   |  |     current    |  |   |
     * |   |  |      page      |  |   |
     * |---+  +----------------+  +---|
     * +------------------------------+
     *
     * @param recyclerView recycler view to attach
     */
    fun attachToRecyclerView(recyclerView: RecyclerView) {
        attachToPager(recyclerView, RecyclerViewAttacher())
    }

    /**
     * Attaches indicator to RecyclerView. Use this method if current page of the recycler isn't centered.
     * All pages must have the same width.
     * Like this:
     *
     *
     * +-|----------------------------+
     * | +--------+  +--------+  +----|
     * | | current|  |        |  |    |
     * | |  page  |  |        |  |    |
     * | +--------+  +--------+  +----|
     * +-|----------------------------+
     * | currentPageOffset
     * |
     *
     * @param recyclerView      recycler view to attach
     * @param currentPageOffset x coordinate of current view left corner/top relative to recycler view
     */
    /*fun attachToRecyclerView(recyclerView: RecyclerView, currentPageOffset: Int) {
        attachToPager(recyclerView, RecyclerViewAttacher(currentPageOffset))
    }*/

    /**
     * Attaches to any custom pager
     *
     * @param pager    pager to attach
     * @param attacher helper which should setup this indicator to work with custom pager
     */
    fun <T> attachToPager(pager: T, attacher: PagerAttacher<T>) {
        detachFromPager()
        attacher.attachToPager(this, pager)
        currentAttacher = attacher

        attachRunnable = Runnable {
            itemCount = -1
            attachToPager(pager, attacher)
        }
    }

    /**
     * Detaches indicator from pager.
     */
    fun detachFromPager() {
        if (currentAttacher != null) {
            currentAttacher!!.detachFromPager()
            currentAttacher = null
            attachRunnable = null
            autoRtl = true
        }
        dotCountInitialized = false
    }

    /**
     * Detaches indicator from pager and attaches it again.
     * It may be useful for refreshing after adapter count change.
     */
    fun reattach() {
        if (attachRunnable != null) {
            attachRunnable!!.run()
            invalidate()
        }
    }

    /**
     * This method must be called from ViewPager.OnPageChangeListener.onPageScrolled or from some
     * similar callback if you use custom PagerAttacher.
     *
     * @param page   index of the first page currently being displayed
     * Page position+1 will be visible if offset is nonzero
     * @param offset Value from [0, 1) indicating the offset from the page at position
     */
    fun onPageScrolled(page: Int, offset: Float) {
        require(!(offset < 0 || offset > 1)) { "Offset must be [0, 1]" }
        if (page < 0 || page != 0 && page >= itemCount) {
            throw IndexOutOfBoundsException("page must be [0, adapter.getItemCount())")
        }

        if (!looped || itemCount <= visibleDotCount && itemCount > 1) {
            dotScale!!.clear()

            if (orientation == LinearLayout.HORIZONTAL) {
                scaleDotByOffset(page, offset)

                if (page < itemCount - 1) {
                    scaleDotByOffset(page + 1, 1 - offset)
                } else if (itemCount > 1) {
                    scaleDotByOffset(0, 1 - offset)
                }
            } else { // Vertical orientation
                scaleDotByOffset(page - 1, offset)
                scaleDotByOffset(page, 1 - offset)
            }

            invalidate()
        }
        if (orientation == LinearLayout.HORIZONTAL) {
            adjustFramePosition(offset, page)
        } else {
            adjustFramePosition(offset, page - 1)
        }
        invalidate()
    }

    /**
     * Sets dot count
     *
     * @param count new dot count
     */
    fun setDotCount(count: Int) {
        initDots(count)
    }

    /**
     * Sets currently selected position (according to your pager's adapter)
     *
     * @param position new current position
     */
    fun setCurrentPosition(position: Int) {
        if (position != 0 && (position < 0 || position >= itemCount)) {
            throw IndexOutOfBoundsException("Position must be [0, adapter.getItemCount()]")
        }
        if (itemCount == 0) {
            return
        }
        adjustFramePosition(0f, position)
        updateScaleInIdleState(position)
    }

    /**
     * Sets Rtl direction availability when the view has Rtl direction.
     * autoRtl is on by default.
     *
     * @param autoRtl false means rtl direction doesn't be apply even if view direction is Rtl.
     */
    fun setAutoRtl(autoRtl: Boolean) {
        this.autoRtl = autoRtl
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Width
        val measuredWidth: Int
        // Height
        val measuredHeight: Int

        if (orientation == LinearLayoutManager.HORIZONTAL) {
            // We ignore widthMeasureSpec because width is based on visibleDotCount
            measuredWidth = if (isInEditMode) {
                // Maximum width with all dots visible
                (visibleDotCount - 1) * spaceBetweenDotCenters + dotSelectedSize
            } else {
                if (itemCount >= visibleDotCount
                ) visibleFrameWidth.toInt() else (itemCount - 1) * spaceBetweenDotCenters + dotSelectedSize
            }
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            val heightSize = MeasureSpec.getSize(heightMeasureSpec)

            // Height
            val desiredHeight = dotSelectedSize

            measuredHeight = when (heightMode) {
                MeasureSpec.EXACTLY -> heightSize
                MeasureSpec.AT_MOST -> min(desiredHeight.toDouble(), heightSize.toDouble()).toInt()
                MeasureSpec.UNSPECIFIED -> desiredHeight
                else -> desiredHeight
            }
        } else {
            measuredHeight = if (isInEditMode) {
                (visibleDotCount - 1) * spaceBetweenDotCenters + dotSelectedSize
            } else {
                if (itemCount >= visibleDotCount
                ) visibleFrameWidth.toInt() else (itemCount - 1) * spaceBetweenDotCenters + dotSelectedSize
            }

            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val widthSize = MeasureSpec.getSize(widthMeasureSpec)

            // Width
            val desiredWidth = dotSelectedSize

            measuredWidth = when (widthMode) {
                MeasureSpec.EXACTLY -> widthSize
                MeasureSpec.AT_MOST -> min(desiredWidth.toDouble(), widthSize.toDouble()).toInt()
                MeasureSpec.UNSPECIFIED -> desiredWidth
                else -> desiredWidth
            }
        }
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val dotCount = getDotCount()
        if (dotCount < visibleDotThreshold) {
            return
        }

        // Some empirical coefficients
        val scaleDistance = (spaceBetweenDotCenters + (dotSelectedSize - dotNormalSize) / 2) * 0.7f
        val smallScaleDistance = (dotSelectedSize / 2).toFloat()
        val centerScaleDistance = 6f / 7f * spaceBetweenDotCenters

        val firstVisibleDotPos =
            (visibleFramePosition - firstDotOffset).toInt() / spaceBetweenDotCenters
        var lastVisibleDotPos = (firstVisibleDotPos
                + (visibleFramePosition + visibleFrameWidth - getDotOffsetAt(firstVisibleDotPos)).toInt() / spaceBetweenDotCenters)

        // If real dots count is less than we can draw inside visible frame, we move lastVisibleDotPos
        // to the last item
        if (firstVisibleDotPos == 0 && lastVisibleDotPos + 1 > dotCount) {
            lastVisibleDotPos = dotCount - 1
        }

        for (i in firstVisibleDotPos..lastVisibleDotPos) {
            val dot = getDotOffsetAt(i)
            if (dot >= visibleFramePosition && dot < visibleFramePosition + visibleFrameWidth) {
                var diameter: Float
                var scale: Float

                // Calculate scale according to current page position
                if (looped && itemCount > visibleDotCount) {
                    val frameCenter = visibleFramePosition + visibleFrameWidth / 2
                    scale = if (dot >= frameCenter - centerScaleDistance
                        && dot <= frameCenter
                    ) {
                        (dot - frameCenter + centerScaleDistance) / centerScaleDistance
                    } else if (dot > frameCenter
                        && dot < frameCenter + centerScaleDistance
                    ) {
                        1 - (dot - frameCenter) / centerScaleDistance
                    } else {
                        0f
                    }
                } else {
                    scale = getDotScaleAt(i)
                }
                diameter = dotNormalSize + (dotSelectedSize - dotNormalSize) * scale

                // Additional scale for dots at corners
                if (itemCount > visibleDotCount) {
                    var currentScaleDistance = if (!looped && (i == 0 || i == dotCount - 1)) {
                        smallScaleDistance
                    } else {
                        scaleDistance
                    }

                    var size = width
                    if (orientation == LinearLayoutManager.VERTICAL) {
                        size = height
                    }
                    if (dot - visibleFramePosition < currentScaleDistance) {
                        val calculatedDiameter =
                            diameter * (dot - visibleFramePosition) / currentScaleDistance
                        if (calculatedDiameter <= dotMinimumSize) {
                            diameter = dotMinimumSize.toFloat()
                        } else if (calculatedDiameter < diameter) {
                            diameter = calculatedDiameter
                        }
                    } else if (dot - visibleFramePosition > size - currentScaleDistance) {
                        val calculatedDiameter =
                            diameter * (-dot + visibleFramePosition + size) / currentScaleDistance
                        if (calculatedDiameter <= dotMinimumSize) {
                            diameter = dotMinimumSize.toFloat()
                        } else if (calculatedDiameter < diameter) {
                            diameter = calculatedDiameter
                        }
                    }
                }

                paint.color = calculateDotColor(scale)

                val dotDrawable = if (i == firstVisibleDotPos) {
                    firstDotDrawable
                } else if (i == lastVisibleDotPos) {
                    lastDotDrawable
                } else {
                    null
                }
                if (dotDrawable != null) {
                    if (orientation == LinearLayoutManager.HORIZONTAL) {
                        dotDrawable.setBounds(
                            (dot - visibleFramePosition - dotSelectedSize / 2).toInt(),
                            measuredHeight / 2 - dotSelectedSize / 2,
                            (dot - visibleFramePosition + dotSelectedSize / 2).toInt(),
                            measuredHeight / 2 + dotSelectedSize / 2
                        )
                    } else {
                        dotDrawable.setBounds(
                            measuredWidth / 2 - dotSelectedSize / 2,
                            (dot - visibleFramePosition - dotSelectedSize / 2).toInt(),
                            measuredWidth / 2 + dotSelectedSize / 2,
                            (dot - visibleFramePosition + dotSelectedSize / 2).toInt()
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                        dotDrawable.setTint(paint.color)

                    }
                    dotDrawable.draw(canvas)
                } else if (orientation == LinearLayoutManager.HORIZONTAL) {
                    var cx = dot - visibleFramePosition
                    if (autoRtl && isRtl()) {
                        cx = width - cx
                    }

                    canvas.drawCircle(
                        cx,
                        (measuredHeight / 2).toFloat(),
                        diameter / 2,
                        paint
                    )
                } else {
                    canvas.drawCircle(
                        (measuredWidth / 2).toFloat(),
                        dot - visibleFramePosition,
                        diameter / 2,
                        paint
                    )
                }
            }
        }
    }

    private fun isRtl(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
                layoutDirection == LAYOUT_DIRECTION_RTL
    }

    @ColorInt
    private fun calculateDotColor(dotScale: Float): Int {
        return colorEvaluator.evaluate(dotScale, dotColor, selectedDotColor) as Int
    }

    private fun updateScaleInIdleState(currentPos: Int) {
        if (!looped || itemCount < visibleDotCount) {
            dotScale!!.clear()
            dotScale!!.put(currentPos, 1f)
            invalidate()
        }
    }

    private fun initDots(itemCount: Int) {
        if (this.itemCount == itemCount && dotCountInitialized) {
            return
        }
        this.itemCount = itemCount
        dotCountInitialized = true
        dotScale = SparseArray()

        if (itemCount < visibleDotThreshold) {
            requestLayout()
            invalidate()
            return
        }

        firstDotOffset =
            (if (looped && this.itemCount > visibleDotCount) 0 else dotSelectedSize / 2).toFloat()
        visibleFrameWidth =
            ((visibleDotCount - 1) * spaceBetweenDotCenters + dotSelectedSize).toFloat()

        requestLayout()
        invalidate()
    }

    private fun getDotCount(): Int {
        return if (looped && itemCount > visibleDotCount) {
            infiniteDotCount
        } else {
            itemCount
        }
    }

    private fun adjustFramePosition(offset: Float, pos: Int) {
        if (itemCount <= visibleDotCount) {
            // Without scroll
            visibleFramePosition = 0f
        } else if (!looped && itemCount > visibleDotCount) {
            // Not looped with scroll
            val center = getDotOffsetAt(pos) + spaceBetweenDotCenters * offset
            visibleFramePosition = center - visibleFrameWidth / 2

            // Block frame offset near start and end
            val firstCenteredDotIndex = visibleDotCount / 2
            val lastCenteredDot = getDotOffsetAt(getDotCount() - 1 - firstCenteredDotIndex)
            if (visibleFramePosition + visibleFrameWidth / 2 < getDotOffsetAt(firstCenteredDotIndex)) {
                visibleFramePosition = getDotOffsetAt(firstCenteredDotIndex) - visibleFrameWidth / 2
            } else if (visibleFramePosition + visibleFrameWidth / 2 > lastCenteredDot) {
                visibleFramePosition = lastCenteredDot - visibleFrameWidth / 2
            }
        } else {
            // Looped with scroll
            val center = getDotOffsetAt(infiniteDotCount / 2) + spaceBetweenDotCenters * offset
            visibleFramePosition = center - visibleFrameWidth / 2
        }
    }

    private fun scaleDotByOffset(position: Int, offset: Float) {
        if (dotScale == null || getDotCount() == 0) {
            return
        }
        setDotScaleAt(position, (1 - abs(offset.toDouble())).toFloat())
    }

    private fun getDotOffsetAt(index: Int): Float {
        return firstDotOffset + index * spaceBetweenDotCenters
    }

    private fun getDotScaleAt(index: Int): Float {
        val scale = dotScale!![index]
        if (scale != null) {
            return scale
        }
        return 0f
    }

    private fun setDotScaleAt(index: Int, scale: Float) {
        if (scale == 0f) {
            dotScale!!.remove(index)
        } else {
            dotScale!!.put(index, scale)
        }
    }

    /**
     * Interface for attaching to custom pagers.
     *
     * @param <T> custom pager's class
    </T> */
    interface PagerAttacher<T> {
        /**
         * Here you should add all needed callbacks to track pager's item count, position and offset
         * You must call:
         * [ScrollingPagerIndicator.setDotCount] - initially and after page selection,
         * [ScrollingPagerIndicator.setCurrentPosition] - initially and after page selection,
         * [ScrollingPagerIndicator.onPageScrolled] - in your pager callback to track scroll offset,
         * [ScrollingPagerIndicator.reattach] - each time your adapter items change.
         *
         * @param indicator indicator
         * @param pager     pager to attach
         */
        fun attachToPager(indicator: ScrollingPagerIndicator, pager: T)

        /**
         * Here you should unregister all callbacks previously added to pager and adapter
         */
        fun detachFromPager()
    }
}
