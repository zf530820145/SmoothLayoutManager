package kt.com.scroliconlayout

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.support.v7.widget.LinearSmoothScroller
import android.support.v7.widget.LinearSnapHelper
import android.support.v7.widget.OrientationHelper
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.SCROLL_STATE_IDLE
import android.util.AttributeSet
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup

class GalleryLayoutManager(orientation: Int) : RecyclerView.LayoutManager(),
    RecyclerView.SmoothScroller.ScrollVectorProvider {

    companion object {
        private const val LAYOUT_START = -1
        private const val LAYOUT_END = 1
        const val HORIZONTAL = OrientationHelper.HORIZONTAL
        const val VERTICAL = OrientationHelper.VERTICAL
    }

    lateinit var mRecyclerView: RecyclerView
    private var firstVisiblePosition = 0
    private var lastVisiblePosition = 0
    private var initialSelectedPosition = 0
    var curSelectedPosition = -1
    var curSelectedView: View? = null
    var state: State = State()
    private var snapHelper: LinearSnapHelper = LinearSnapHelper()
    private var innerScrollListener = InnerScrollListener()
    var orientation = HORIZONTAL
    var callbackInFling = false

    lateinit private var horizontalHelper: OrientationHelper
    lateinit private var verticalHelper: OrientationHelper
    var onItemSelectedListener: OnItemSelectedListener? = null
    var itemTransformer: ItemTransformer? = null
    private val horizontalSpace: Int
        get() = width - paddingRight - paddingLeft


    private val verticalSpace: Int
        get() = height - paddingBottom - paddingTop


    init {
        this.orientation = orientation
        if (orientation == GalleryLayoutManager.HORIZONTAL) {
            horizontalHelper = OrientationHelper.createHorizontalHelper(this)
        } else {
            verticalHelper = OrientationHelper.createVerticalHelper(this)
        }
    }

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return if (this.orientation == VERTICAL) {
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        } else {
            LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    override fun checkLayoutParams(lp: RecyclerView.LayoutParams?): Boolean {
        return lp is LayoutParams
    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
        recycler?.let { ry ->
            if (itemCount == 0) {
                reset()
                //TODO 3
                detachAndScrapAttachedViews(ry)
                return
            }
            state?.let {
                if (it.isPreLayout)
                    return
                if (it.itemCount != 0 && !it.didStructureChange())
                    return
                if (childCount == 0 || it.didStructureChange())
                    reset()
                initialSelectedPosition = Math.min(Math.max(0, initialSelectedPosition), itemCount - 1)
                detachAndScrapAttachedViews(ry)
                firstFillCover(ry, it, 0f)
            }
        }
    }

    private fun reset() {
        state.itemFrames.clear()
        if (curSelectedPosition != -1) initialSelectedPosition = curSelectedPosition
        initialSelectedPosition = Math.min(Math.max(0, initialSelectedPosition), itemCount - 1)
        firstVisiblePosition = initialSelectedPosition
        lastVisiblePosition = initialSelectedPosition
        curSelectedPosition = -1
        if (curSelectedView != null) {
            curSelectedView?.isSelected = false
            curSelectedView = null
        }
    }

     private fun getOrientationHelper(): OrientationHelper {
         return if (orientation == HORIZONTAL) horizontalHelper else verticalHelper
     }


    override fun generateLayoutParams(lp: ViewGroup.LayoutParams): RecyclerView.LayoutParams {
        return (lp as? ViewGroup.MarginLayoutParams)?.let { GalleryLayoutManager.LayoutParams(it) }
            ?: GalleryLayoutManager.LayoutParams(lp)
    }


    private fun firstFillCover(recycler: RecyclerView.Recycler, state: RecyclerView.State, scrollDelta: Float) {
        if (orientation == HORIZONTAL) {
            firstFillWithHorizontal(recycler, state)
        } else {
            firstFillWithVertical(recycler, state)
        }
        itemTransformer?.let { item ->
            var child: View?
            for (i in 0 until childCount) {
                child = getChildAt(i)
                child?.let {
                    item.transformItem(this, it, calculateToCenterFraction(it, scrollDelta))
                }
            }
        }
        innerScrollListener.onScrolled(mRecyclerView, 0, 0)
    }

    private fun firstFillWithHorizontal(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        detachAndScrapAttachedViews(recycler)
        val scrapWidth: Int
        val scrapHeight: Int
        val topOffset: Int
        val startPosition = initialSelectedPosition
        val scrapRect = Rect()
        val leftEdge = getOrientationHelper().startAfterPadding
        val rightEdge = getOrientationHelper().endAfterPadding
        val height = verticalSpace
        val scrap: View = recycler.getViewForPosition(initialSelectedPosition)
        addView(scrap, 0)
        measureChildWithMargins(scrap, 0, 0)
        scrapWidth = getDecoratedMeasuredWidth(scrap)
        scrapHeight = getDecoratedMeasuredHeight(scrap)
        topOffset = (paddingTop + (height - scrapHeight) / 2f).toInt()
        val left = (paddingLeft + (horizontalSpace - scrapWidth) / 2f).toInt()
        scrapRect.set(left, topOffset, left + scrapWidth, topOffset + scrapHeight)
        layoutDecorated(scrap, scrapRect.left, scrapRect.top, scrapRect.right, scrapRect.bottom)
        if (this.state.itemFrames.get(startPosition) == null) {
            this.state.itemFrames.put(startPosition, scrapRect)
        } else {
            this.state.itemFrames.get(startPosition).set(scrapRect)
        }
        lastVisiblePosition = startPosition
        firstVisiblePosition = lastVisiblePosition
        val leftStartOffset = getDecoratedLeft(scrap)
        val rightStartOffset = getDecoratedRight(scrap)
        fillLeft(recycler, initialSelectedPosition - 1, leftStartOffset, leftEdge)
        fillRight(recycler, initialSelectedPosition + 1, rightStartOffset, rightEdge)
    }

    private fun firstFillWithVertical(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        detachAndScrapAttachedViews(recycler)
        val topEdge = getOrientationHelper().startAfterPadding
        val bottomEdge = getOrientationHelper().endAfterPadding
        val scrapWidth: Int
        val scrapHeight: Int
        val leftOffset: Int
        val startPosition = initialSelectedPosition
        val scrapRect = Rect()
        val width = horizontalSpace
        val scrap: View = recycler.getViewForPosition(initialSelectedPosition)
        addView(scrap, 0)
        measureChildWithMargins(scrap, 0, 0)
        scrapWidth = getDecoratedMeasuredWidth(scrap)
        scrapHeight = getDecoratedMeasuredHeight(scrap)
        leftOffset = (paddingLeft + (width - scrapWidth) / 2f).toInt()
        val top = (paddingTop + (verticalSpace - scrapHeight) / 2f).toInt()
        scrapRect.set(leftOffset, top, leftOffset + scrapWidth, top + scrapHeight)
        layoutDecorated(scrap, scrapRect.left, scrapRect.top, scrapRect.right, scrapRect.bottom)
        if (this.state.itemFrames.get(startPosition) == null) {
            this.state.itemFrames.put(startPosition, scrapRect)
        } else {
            this.state.itemFrames.get(startPosition).set(scrapRect)
        }
        lastVisiblePosition = startPosition
        firstVisiblePosition = lastVisiblePosition
        val topStartOffset = getDecoratedTop(scrap)
        val bottomStartOffset = getDecoratedBottom(scrap)
        fillTop(recycler, initialSelectedPosition - 1, topStartOffset, topEdge)
        fillBottom(recycler, initialSelectedPosition + 1, bottomStartOffset, bottomEdge)
    }

    private fun fillLeft(recycler: RecyclerView.Recycler, position: Int, offect: Int, leftEdge: Int) {
        var scrap: View?
        var topOffset: Int
        var scrapWidth: Int
        var scrapHeight: Int
        val scrapRect = Rect()
        val height = verticalSpace
        var startPosition = position
        var startOffset = offect
        while (startPosition >= 0 && startOffset > leftEdge) {
            scrap = recycler.getViewForPosition(startPosition)
            addView(scrap, 0)
            measureChildWithMargins(scrap, 0, 0)
            scrapWidth = getDecoratedMeasuredWidth(scrap)
            scrapHeight = getDecoratedMeasuredHeight(scrap)
            topOffset = (paddingTop + (height - scrapHeight) / 2.0f).toInt()
            scrapRect.set(startOffset - scrapWidth, topOffset, startOffset, topOffset + scrapHeight)
            layoutDecorated(scrap, scrapRect.left, scrapRect.top, scrapRect.right, scrapRect.bottom)
            startOffset = scrapRect.left
            firstVisiblePosition = startPosition
            if (this.state.itemFrames.get(startPosition) == null) {
                state.itemFrames.put(startPosition, scrapRect)
            } else {
                state.itemFrames.get(startPosition).set(scrapRect)
            }
            startPosition--
        }
    }

    private fun fillRight(recycler: RecyclerView.Recycler, postion: Int, offset: Int, rightEdge: Int) {
        var startOffset = offset
        var scrap: View
        var topOffset: Int
        var scrapWidth: Int
        var scrapHeight: Int
        val scrapRect = Rect()
        val height = verticalSpace
        var startPosition = postion
        while (startPosition < itemCount && startOffset < rightEdge) {
            scrap = recycler.getViewForPosition(startPosition)
            addView(scrap)
            measureChildWithMargins(scrap, 0, 0)
            scrapWidth = getDecoratedMeasuredWidth(scrap)
            scrapHeight = getDecoratedMeasuredHeight(scrap)
            topOffset = (paddingTop + (height - scrapHeight) / 2.0f).toInt()
            scrapRect.set(startOffset, topOffset, startOffset + scrapWidth, topOffset + scrapHeight)
            layoutDecorated(scrap, scrapRect.left, scrapRect.top, scrapRect.right, scrapRect.bottom)
            startOffset = scrapRect.right
            lastVisiblePosition = startPosition
            if (state.itemFrames.get(startPosition) == null) {
                state.itemFrames.put(startPosition, scrapRect)
            } else {
                state.itemFrames.get(startPosition).set(scrapRect)
            }
            startPosition++
        }
    }

    private fun fillTop(recycler: RecyclerView.Recycler, position: Int, offset: Int, topEdge: Int) {
        var startOffset = offset
        var scrap: View
        var leftOffset: Int
        var scrapWidth: Int
        var scrapHeight: Int
        val scrapRect = Rect()
        val width = horizontalSpace
        var startPosition = position
        while (startPosition >= 0 && startOffset > topEdge) {
            scrap = recycler.getViewForPosition(startPosition)
            addView(scrap, 0)
            measureChildWithMargins(scrap, 0, 0)
            scrapWidth = getDecoratedMeasuredWidth(scrap)
            scrapHeight = getDecoratedMeasuredHeight(scrap)
            leftOffset = (paddingLeft + (width - scrapWidth) / 2.0f).toInt()
            scrapRect.set(leftOffset, startOffset - scrapHeight, leftOffset + scrapWidth, startOffset)
            layoutDecorated(scrap, scrapRect.left, scrapRect.top, scrapRect.right, scrapRect.bottom)
            startOffset = scrapRect.top
            firstVisiblePosition = startPosition
            if (state.itemFrames.get(startPosition) == null) {
                state.itemFrames.put(startPosition, scrapRect)
            } else {
                state.itemFrames.get(startPosition).set(scrapRect)
            }
            startPosition--
        }
    }

    private fun fillBottom(recycler: RecyclerView.Recycler, position: Int, offset: Int, bottomEdge: Int) {
        var startOffset = offset
        var scrap: View
        var leftOffset: Int
        var scrapWidth: Int
        var scrapHeight: Int
        val scrapRect = Rect()
        val width = horizontalSpace
        var startPosition = position
        while (startPosition < itemCount && startOffset < bottomEdge) {
            scrap = recycler.getViewForPosition(startPosition)
            addView(scrap)
            measureChildWithMargins(scrap, 0, 0)
            scrapWidth = getDecoratedMeasuredWidth(scrap)
            scrapHeight = getDecoratedMeasuredHeight(scrap)
            leftOffset = (paddingLeft + (width - scrapWidth) / 2.0f).toInt()
            scrapRect.set(leftOffset, startOffset, leftOffset + scrapWidth, startOffset + scrapHeight)
            layoutDecorated(scrap, scrapRect.left, scrapRect.top, scrapRect.right, scrapRect.bottom)
            startOffset = scrapRect.bottom
            lastVisiblePosition = startPosition
            if (state.itemFrames.get(startPosition) == null) {
                state.itemFrames.put(startPosition, scrapRect)
            } else {
                state.itemFrames.get(startPosition).set(scrapRect)
            }
            startPosition++
        }
    }

    private fun fillCover(recycler: RecyclerView.Recycler?, state: RecyclerView.State?, scrollDelta: Float) {
        if (itemCount == 0) return
        recycler?.let {
            if (orientation == HORIZONTAL) {
                fillWithHorizontal(it, state, scrollDelta)
            } else {
                fillWithVertical(it, state, scrollDelta)
            }
        }

        if (itemTransformer != null) {
            var child: View?
            for (i in 0 until childCount) {
                child = getChildAt(i)
                child?.let {
                    itemTransformer?.transformItem(this, it, calculateToCenterFraction(it, scrollDelta))
                }
            }
        }

    }

    private fun calculateToCenterFraction(child: View, pendingOffset: Float): Float {
        val distance = calculateDistanceCenter(child, pendingOffset)
        val childLength = if (orientation == GalleryLayoutManager.HORIZONTAL) child.width else child.height
        return Math.max(-1f, Math.min(1f, distance * 1f / childLength))
    }

    private fun calculateDistanceCenter(child: View, pendingOffset: Float): Float {
        val parentCenter =
            (getOrientationHelper().endAfterPadding - getOrientationHelper().startAfterPadding) / 2 + getOrientationHelper().startAfterPadding
        return if (orientation == HORIZONTAL) {
            (child.width / 2 - pendingOffset + child.left - parentCenter)
        } else {
            (child.height / 2 - pendingOffset + child.top - parentCenter)
        }

    }

    private fun fillWithVertical(recycler: RecyclerView.Recycler, state: RecyclerView.State?, scrollDelta: Float) {
        val topEdge = getOrientationHelper().startAfterPadding
        val bottomEdge = getOrientationHelper().endAfterPadding
        var child: View?
        if (childCount > 0) {
            if (scrollDelta >= 0) {
                var fixIndex = 0
                 for (i in 0..childCount) {
                    child = getChildAt(i + fixIndex)
                    if (child != null) {
                        if (getDecoratedBottom(child) - scrollDelta < topEdge) {
                            removeAndRecycleView(child, recycler)
                            firstVisiblePosition++
                            fixIndex--
                        } else {
                            break
                        }
                    }
                }
            } else {
                for (i in childCount - 1 downTo 0) {
                    child = getChildAt(i)
                    if (child != null) {
                        if (getDecoratedTop(child) - scrollDelta > bottomEdge) {
                            removeAndRecycleView(child, recycler)
                            lastVisiblePosition--
                        } else {
                            break
                        }
                    }
                }
            }
        }
        var startPosition = firstVisiblePosition
        var startOffset = -1
        var scrapWidth: Int
        var scrapHeight: Int
        var scrapRect: Rect?
        val width = horizontalSpace
        var leftOffset: Int
        var scrap: View

        if (scrollDelta >= 0) {
            if (childCount != 0) {
                val lastView: View? = getChildAt(childCount - 1)
                lastView?.let {
                    startPosition = getPosition(it) + 1
                    startOffset = getDecoratedBottom(it)
                }
            }
            var i = startPosition
            while (i < itemCount && startOffset < bottomEdge + scrollDelta) {
                scrapRect = this.state.itemFrames.get(i)
                scrap = recycler.getViewForPosition(i)
                addView(scrap)

                if (scrapRect == null) {
                    scrapRect = Rect()
                    this.state.itemFrames.put(i, scrapRect)
                }
                measureChildWithMargins(scrap, 0, 0)
                scrapWidth = getDecoratedMeasuredWidth(scrap)
                scrapHeight = getDecoratedMeasuredHeight(scrap)
                leftOffset = (paddingLeft + (width - scrapWidth) / 2.0f).toInt()
                if (startOffset == -1 && startPosition == 0) {
                    //layout the first position item in center
                    val top = (paddingTop + (verticalSpace - scrapHeight) / 2f).toInt()
                    scrapRect.set(leftOffset, top, leftOffset + scrapWidth, top + scrapHeight)
                } else {
                    scrapRect.set(leftOffset, startOffset, leftOffset + scrapWidth, startOffset + scrapHeight)
                }
                layoutDecorated(scrap, scrapRect.left, scrapRect.top, scrapRect.right, scrapRect.bottom)
                startOffset = scrapRect.bottom
                lastVisiblePosition = i
                i++
            }
        } else {
            if (childCount > 0) {
                val firstView = getChildAt(0)
                firstView?.let {
                    startPosition = getPosition(it) - 1 //前一个View的position
                    startOffset = getDecoratedTop(it)
                }
                var i = startPosition
                while (i >= 0 && startOffset > topEdge + scrollDelta) {
                    scrapRect = this.state.itemFrames.get(i)
                    scrap = recycler.getViewForPosition(i)
                    addView(scrap, 0)
                    if (scrapRect == null) {
                        scrapRect = Rect()
                        this.state.itemFrames.put(i, scrapRect)
                    }
                    measureChildWithMargins(scrap, 0, 0)
                    scrapWidth = getDecoratedMeasuredWidth(scrap)
                    scrapHeight = getDecoratedMeasuredHeight(scrap)
                    leftOffset = (paddingLeft + (width - scrapWidth) / 2.0f).toInt()
                    scrapRect.set(leftOffset, startOffset - scrapHeight, leftOffset + scrapWidth, startOffset)
                    layoutDecorated(scrap, scrapRect.left, scrapRect.top, scrapRect.right, scrapRect.bottom)
                    startOffset = scrapRect.top
                    firstVisiblePosition = i
                    i--
                }
            }
        }

    }

    private fun fillWithHorizontal(recycler: RecyclerView.Recycler, state: RecyclerView.State?, scrollDelta: Float) {
        val leftEdge = getOrientationHelper().startAfterPadding
        val rightEdge = getOrientationHelper().endAfterPadding
        var child: View?
        if (childCount > 0) {
            if (scrollDelta >= 0) {
                var fixIndex = 0
                for (i in 0 until childCount) {
                    child = getChildAt(i + fixIndex)
                    if (child != null) {
                        if (getDecoratedRight(child) - scrollDelta < leftEdge) {
                            removeAndRecycleView(child, recycler)
                            firstVisiblePosition++
                            fixIndex--

                        } else {
                            break
                        }
                    }

                }
            } else {
                for (i in childCount - 1 downTo 0) {
                    child = getChildAt(i)
                    if (child != null) {
                        if (getDecoratedLeft(child) - scrollDelta > rightEdge) {
                            removeAndRecycleView(child, recycler)
                            lastVisiblePosition--
                        }
                    }

                }
            }

        }
        var startPosition = firstVisiblePosition
        var startOffset = -1
        var scrapWidth: Int
        var scrapHeight: Int
        var scrapRect: Rect?
        val height = verticalSpace
        var topOffset: Int
        var scrap: View
        if (scrollDelta >= 0) {
            if (childCount != 0) {
                val lastView = getChildAt(childCount - 1)
                startPosition = getPosition(lastView!!) + 1 //start layout from next position item
                startOffset = getDecoratedRight(lastView)

            }
            var i = startPosition
            while (i < itemCount && startOffset < rightEdge + scrollDelta) {
                scrapRect = this.state.itemFrames.get(i)
                scrap = recycler.getViewForPosition(i)
                addView(scrap)
                if (scrapRect == null) {
                    scrapRect = Rect()
                    this.state.itemFrames.put(i, scrapRect)
                }
                measureChildWithMargins(scrap, 0, 0)
                scrapWidth = getDecoratedMeasuredWidth(scrap)
                scrapHeight = getDecoratedMeasuredHeight(scrap)
                topOffset = (paddingTop + (height - scrapHeight) / 2.0f).toInt()
                if (startOffset == -1 && startPosition == 0) {
                    // layout the first position item in center
                    val left = (paddingLeft + (horizontalSpace - scrapWidth) / 2f).toInt()
                    scrapRect.set(left, topOffset, left + scrapWidth, topOffset + scrapHeight)
                } else {
                    scrapRect.set(startOffset, topOffset, startOffset + scrapWidth, topOffset + scrapHeight)
                }
                layoutDecorated(scrap, scrapRect.left, scrapRect.top, scrapRect.right, scrapRect.bottom)
                startOffset = scrapRect.right
                lastVisiblePosition = i
                i++
            }
        } else {
            if (childCount > 0) {
                val firstView = getChildAt(0)
                startPosition = getPosition(firstView!!) - 1 //start layout from previous position item
                startOffset = getDecoratedLeft(firstView)

            }
            var i = startPosition
            while (i >= 0 && startOffset > leftEdge + scrollDelta) {
                scrapRect = this.state.itemFrames.get(i)
                scrap = recycler.getViewForPosition(i)
                addView(scrap, 0)
                if (scrapRect == null) {
                    scrapRect = Rect()
                    this.state.itemFrames.put(i, scrapRect)
                }
                measureChildWithMargins(scrap, 0, 0)
                scrapWidth = getDecoratedMeasuredWidth(scrap)
                scrapHeight = getDecoratedMeasuredHeight(scrap)
                topOffset = (paddingTop + (height - scrapHeight) / 2.0f).toInt()
                scrapRect.set(startOffset - scrapWidth, topOffset, startOffset, topOffset + scrapHeight)
                layoutDecorated(scrap, scrapRect.left, scrapRect.top, scrapRect.right, scrapRect.bottom)
                startOffset = scrapRect.left
                firstVisiblePosition = i
                i--
            }
        }
    }

    private fun calculateScrollDirectionForPosition(position: Int): Int {
        if (childCount == 0) return LAYOUT_START
        val firstChildPos = firstVisiblePosition
        return if (position < firstChildPos) LAYOUT_START else LAYOUT_END
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        val direction = calculateScrollDirectionForPosition(targetPosition)
        val outVector = PointF()
        if (direction == 0)
            return null
        if (orientation == HORIZONTAL) {
            outVector.x = direction.toFloat()
            outVector.y = 0f
        } else {
            outVector.x = 0f
            outVector.y = direction.toFloat()
        }
        return outVector
    }

    inner class State {
        var itemFrames: SparseArray<Rect>
        var scrollDelta: Int

        init {
            itemFrames = SparseArray()
            scrollDelta = 0
        }

    }

    override fun canScrollVertically(): Boolean {
        return orientation == VERTICAL
    }

    override fun canScrollHorizontally(): Boolean {
        return orientation == HORIZONTAL
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?): Int {
        if (childCount == 0 || dx == 0) return 0
        var delta = -dx
        val parentCenter =
            (getOrientationHelper().endAfterPadding - getOrientationHelper().startAfterPadding) / 2 + getOrientationHelper().startAfterPadding
        val child: View?
        if (dx > 0) {
            val childAt = getChildAt(childCount - 1)
            if (childAt != null) {
                if (getPosition(childAt) == itemCount - 1) {
                    child = childAt
                    delta = -Math.max(0, Math.min(dx, (child.right - child.left) / 2 + child.left - parentCenter))
                }
            }
        } else {
            if (firstVisiblePosition == 0) {
                child = getChildAt(0)
                if (child != null)
                    delta = -Math.min(0, Math.max(dx, (child.right - child.left) / 2 + child.left - parentCenter))
            }
        }
        this.state.scrollDelta = -delta
        fillCover(recycler, state, -delta.toFloat())
        offsetChildrenHorizontal(delta)
        return -delta
    }

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?): Int {
        if (childCount == 0 || dy == 0) {
            return 0
        }
        var delta = -dy
        val parentCenter =
            (getOrientationHelper().endAfterPadding - getOrientationHelper().startAfterPadding) / 2 + getOrientationHelper().startAfterPadding
        val child: View?
        if (dy > 0) {
            if (getPosition(getChildAt(childCount - 1)!!) == itemCount - 1) {
                child = getChildAt(childCount - 1)
                delta = -Math.max(
                    0,
                    Math.min(
                        dy,
                        (getDecoratedBottom(child!!) - getDecoratedTop(child)) / 2 + getDecoratedTop(child) - parentCenter
                    )
                )
            }
        } else {
            //If we've reached the first item, enforce limits
            if (firstVisiblePosition == 0) {
                child = getChildAt(0)
                delta = -Math.min(
                    0,
                    Math.max(
                        dy,
                        (getDecoratedBottom(child!!) - getDecoratedTop(child)) / 2 + getDecoratedTop(child) - parentCenter
                    )
                )
            }
        }
        this.state.scrollDelta = -delta
        fillCover(recycler, state, -delta.toFloat())
        offsetChildrenVertical(delta)
        return -delta
    }

    fun attach(recyclerView: RecyclerView?) {
        this.attach(recyclerView, -1)
    }

    fun attach(recyclerView: RecyclerView?, selectedPosition: Int) {
        if (recyclerView == null) {
            throw IllegalArgumentException("The attach RecycleView must not null!!")
        }
        mRecyclerView = recyclerView
        initialSelectedPosition = Math.max(0, selectedPosition)
        recyclerView.layoutManager = this
        snapHelper.attachToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(innerScrollListener)
    }

    interface OnItemSelectedListener {
        fun onItemSelected(recycler: RecyclerView, item: View, position: Int)
    }

    interface ItemTransformer {
        fun transformItem(galleryLayoutManager: GalleryLayoutManager, child: View, calculateToCenterFraction: Float)
    }

    private inner class InnerScrollListener : RecyclerView.OnScrollListener() {
        var state: Int = 0
        var callbackOnIdle: Boolean = false
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val snapView = snapHelper.findSnapView(recyclerView.layoutManager)
            if (snapView != null && recyclerView.layoutManager != null) {
                val selectedPosition = recyclerView.layoutManager?.getPosition(snapView)
                if (selectedPosition != curSelectedPosition) {
                    if (curSelectedView != null) curSelectedView?.isSelected = false
                    curSelectedView = snapView
                    curSelectedView?.isSelected = true
                    curSelectedPosition = selectedPosition ?: 0
                    if (!callbackInFling && state != SCROLL_STATE_IDLE) {
                        callbackOnIdle = true
                        return
                    }
                    onItemSelectedListener?.onItemSelected(recyclerView, snapView, curSelectedPosition)

                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            this.state = newState
            if (state == SCROLL_STATE_IDLE) {
                val snap = snapHelper.findSnapView(recyclerView.layoutManager)
                if (snap != null) {
                    val selectedPosition = recyclerView.layoutManager?.getPosition(snap)
                    if (selectedPosition != curSelectedPosition) {
                        if (curSelectedView != null) {
                            curSelectedView?.isSelected = false
                        }
                        curSelectedView = snap
                        curSelectedView?.isSelected = true
                        curSelectedPosition = selectedPosition ?: 0
                        onItemSelectedListener?.onItemSelected(recyclerView, snap, curSelectedPosition)
                    } else if (!callbackInFling && onItemSelectedListener != null && callbackOnIdle) {
                        callbackOnIdle = false
                        onItemSelectedListener?.onItemSelected(recyclerView, snap, curSelectedPosition)
                    }
                }
            }

        }
    }

    override fun smoothScrollToPosition(recyclerView: RecyclerView?, state: RecyclerView.State?, position: Int) {
        recyclerView?.let {
            val linearSmoothScroller = GallerySmoothScroller(it.context)
            linearSmoothScroller.targetPosition = position
            startSmoothScroll(linearSmoothScroller)
        }
    }

    inner class GallerySmoothScroller(context: Context?) : LinearSmoothScroller(context) {
        fun calculateDxToMakeCentral(view: View): Int {
            val layoutManager = layoutManager
            if (layoutManager == null || !(layoutManager.canScrollHorizontally())) return 0
            val params = view.layoutParams as RecyclerView.LayoutParams
            val left = layoutManager.getDecoratedLeft(view) - params.leftMargin
            val right = layoutManager.getDecoratedRight(view) + params.rightMargin
            val start = layoutManager.paddingLeft
            val end = layoutManager.width - layoutManager.paddingRight
            val childCenter = left + ((right - left) / 2.0f).toInt()
            val containerCenter = ((end - start) / 2f).toInt()
            return containerCenter - childCenter
        }

        fun calculateDyToMakeCentral(view: View): Int {
            val layoutManager = layoutManager
            if (layoutManager == null || !(layoutManager.canScrollVertically())) return 0
            val params = view.layoutParams as RecyclerView.LayoutParams
            val top = layoutManager.getDecoratedTop(view) - params.topMargin
            val bottom = layoutManager.getDecoratedBottom(view) + params.bottomMargin
            val start = layoutManager.paddingTop
            val end = layoutManager.height - layoutManager.paddingBottom
            val childCenter = top + ((bottom - top) / 2.0f).toInt()
            val containerCenter = ((end - start) / 2f).toInt()
            return containerCenter - childCenter
        }

        override fun onTargetFound(targetView: View, state: RecyclerView.State, action: Action) {
            val dx = calculateDxToMakeCentral(targetView)
            val dy = calculateDyToMakeCentral(targetView)
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
            val time = calculateTimeForDeceleration(distance.toInt())
            if (time > 0)
                action.update(-dx, -dy, time, mDecelerateInterpolator)
        }
    }

    class LayoutParams : RecyclerView.LayoutParams {
        constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.MarginLayoutParams?) : super(source)
        constructor(source: ViewGroup.LayoutParams?) : super(source)
        constructor(source: RecyclerView.LayoutParams?) : super(source)
    }

}