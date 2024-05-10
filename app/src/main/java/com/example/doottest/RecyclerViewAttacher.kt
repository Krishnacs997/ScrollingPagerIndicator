package com.example.doottest;

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.example.doottest.ScrollingPagerIndicator.PagerAttacher

/**
 * @author Krishna Chaudhary
 * Attacher for RecyclerView.
 */
class RecyclerViewAttacher : PagerAttacher<RecyclerView?> {
    private var indicator: ScrollingPagerIndicator? = null
    private var recyclerView: RecyclerView? = null
    private var layoutManager: LinearLayoutManager? = null
    private var attachedAdapter: RecyclerView.Adapter<*>? = null

    private var scrollListener: RecyclerView.OnScrollListener? = null
    private var dataObserver: AdapterDataObserver? = null

    private val centered: Boolean
    private val currentPageOffset: Int

    private var measuredChildWidth = 0
    private var measuredChildHeight = 0

    /**
     * Default constructor. Use this if current page in recycler is centered.
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
     */
    constructor() {
        currentPageOffset = 0 // Unused when centered
        centered = true
    }

    /**
     * Use this constructor if current page in recycler isn't centered.
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
     * @param currentPageOffset x coordinate of current view left corner/top relative to recycler view.
     */
    constructor(currentPageOffset: Int) {
        this.currentPageOffset = currentPageOffset
        this.centered = false
    }

    override fun attachToPager(indicator: ScrollingPagerIndicator, pager: RecyclerView?) {
        check(pager?.layoutManager is LinearLayoutManager) { "Only LinearLayoutManager is supported" }
        checkNotNull(pager?.adapter) { "RecyclerView has not Adapter attached" }
        this.layoutManager = pager?.layoutManager as LinearLayoutManager?
        this.recyclerView = pager
        this.attachedAdapter = pager?.adapter
        this.indicator = indicator

        dataObserver = object : AdapterDataObserver() {
            override fun onChanged() {
                indicator.setDotCount(attachedAdapter!!.itemCount)
                updateCurrentOffset()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                onChanged()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                onChanged()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                onChanged()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                onChanged()
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                onChanged()
            }
        }
        attachedAdapter!!.registerAdapterDataObserver(dataObserver as AdapterDataObserver)
        indicator.setDotCount(attachedAdapter!!.itemCount)
        updateCurrentOffset()

        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE && isInIdleState()) {
                    val newPosition = findCompletelyVisiblePosition()
                    if (newPosition != RecyclerView.NO_POSITION) {
                        indicator.setDotCount(attachedAdapter!!.itemCount)
                        if (newPosition < attachedAdapter!!.itemCount) {
                            indicator.setCurrentPosition(newPosition)
                        }
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                updateCurrentOffset()
            }
        }

        recyclerView!!.addOnScrollListener(scrollListener as RecyclerView.OnScrollListener)
    }

    override fun detachFromPager() {
        attachedAdapter!!.unregisterAdapterDataObserver(dataObserver!!)
        recyclerView!!.removeOnScrollListener(scrollListener!!)
        measuredChildWidth = 0
    }

    private fun updateCurrentOffset() {
        val firstView = findFirstVisibleView() ?: return

        var position = recyclerView!!.getChildAdapterPosition(firstView)
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val itemCount = attachedAdapter!!.itemCount

        // In case there is an infinite pager
        if (position >= itemCount && itemCount != 0) {
            position = position % itemCount
        }
        val offset = if (layoutManager!!.orientation == LinearLayoutManager.HORIZONTAL) {
            (getCurrentFrameLeft() - firstView.x) / firstView.measuredWidth
        } else {
            (getCurrentFrameBottom() - firstView.y) / firstView.measuredHeight
        }

        if (offset >= 0 && offset <= 1 && position < itemCount) {
            indicator!!.onPageScrolled(position, offset)
        }
    }

    private fun findCompletelyVisiblePosition(): Int {
        for (i in 0 until recyclerView!!.childCount) {
            val child = recyclerView!!.getChildAt(i)

            var position = child.x
            var size = child.measuredWidth
            var currentStart = getCurrentFrameLeft()
            var currentEnd = getCurrentFrameRight()
            if (layoutManager!!.orientation == LinearLayoutManager.VERTICAL) {
                position = child.y
                size = child.measuredHeight
                currentStart = getCurrentFrameTop()
                currentEnd = getCurrentFrameBottom()
            }

            if (position >= currentStart && position + size <= currentEnd) {
                val holder = recyclerView!!.findContainingViewHolder(child)
                if (holder != null && holder.adapterPosition != RecyclerView.NO_POSITION) {
                    return holder.adapterPosition
                }
            }
        }
        return RecyclerView.NO_POSITION
    }

    private fun isInIdleState(): Boolean {
        return findCompletelyVisiblePosition() != RecyclerView.NO_POSITION
    }

    private fun findFirstVisibleView(): View? {
        val childCount = layoutManager!!.childCount
        if (childCount == 0) {
            return null
        }

        var closestChild: View? = null
        var firstVisibleChild = Int.MAX_VALUE

        for (i in 0 until childCount) {
            val child = layoutManager!!.getChildAt(i)

            if (layoutManager!!.orientation == LinearLayoutManager.HORIZONTAL) {
                // Default implementation change: use getX instead of helper
                val childStart = child!!.x.toInt()

                // if child is more to start than previous closest, set it as closest

                // Default implementation change:
                // Fix for any count of visible items
                // We make assumption that all children have the same width
                if (childStart + child.measuredWidth < firstVisibleChild
                    && childStart + child.measuredWidth >= getCurrentFrameLeft()
                ) {
                    firstVisibleChild = childStart
                    closestChild = child
                }
            } else {
                // Default implementation change: use geetY instead of helper
                val childStart = child!!.y.toInt()

                // if child is more to top than previous closest, set it as closest

                // Default implementation change:
                // Fix for any count of visible items
                // We make assumption that all children have the same height
                if (childStart + child.measuredHeight < firstVisibleChild
                    && childStart + child.measuredHeight >= getCurrentFrameBottom()
                ) {
                    firstVisibleChild = childStart
                    closestChild = child
                }
            }
        }

        return closestChild
    }

    private fun getCurrentFrameLeft(): Float {
        return if (centered) {
            (recyclerView!!.measuredWidth - getChildWidth()) / 2
        } else {
            currentPageOffset.toFloat()
        }
    }

    private fun getCurrentFrameRight(): Float {
        return if (centered) {
            (recyclerView!!.measuredWidth - getChildWidth()) / 2 + getChildWidth()
        } else {
            currentPageOffset + getChildWidth()
        }
    }

    private fun getCurrentFrameTop(): Float {
        return if (centered) {
            (recyclerView!!.measuredHeight - getChildHeight()) / 2
        } else {
            currentPageOffset.toFloat()
        }
    }

    private fun getCurrentFrameBottom(): Float {
        return if (centered) {
            (recyclerView!!.measuredHeight - getChildHeight()) / 2 + getChildHeight()
        } else {
            currentPageOffset + getChildHeight()
        }
    }

    private fun getChildWidth(): Float {
        if (measuredChildWidth == 0) {
            for (i in 0 until recyclerView!!.childCount) {
                val child = recyclerView!!.getChildAt(i)
                if (child.measuredWidth != 0) {
                    measuredChildWidth = child.measuredWidth
                    return measuredChildWidth.toFloat()
                }
            }
        }
        return measuredChildWidth.toFloat()
    }

    private fun getChildHeight(): Float {
        if (measuredChildHeight == 0) {
            for (i in 0 until recyclerView!!.childCount) {
                val child = recyclerView!!.getChildAt(i)
                if (child.measuredHeight != 0) {
                    measuredChildHeight = child.measuredHeight
                    return measuredChildHeight.toFloat()
                }
            }
        }
        return measuredChildHeight.toFloat()
    }
}
