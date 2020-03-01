package com.tunjid.androidx.recyclerview.multiscroll

import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import androidx.core.view.doOnDetach
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.tunjid.androidx.recyclerview.R

interface CellSizer {
    val orientation: Int

    fun clear()

    fun sizeAt(position: Int): Int

    fun include(recyclerView: RecyclerView)

    fun exclude(recyclerView: RecyclerView)

    companion object {
        const val UNKNOWN = -1
        const val DETACHED_SIZE = 80
    }
}

internal interface ViewModifier {
    val orientation: Int

    fun View.updateSize(updatedSize: Int) {
        val layoutParams = layoutParams
        val currentSize = if (isHorizontal) layoutParams.width else layoutParams.height

        if (currentSize == updatedSize) return

        invalidate()
        updateLayoutParams { if (isHorizontal) width = updatedSize else height = updatedSize }

        onParentIdle { requestLayout() }
    }

    fun View.log(action: String, filter: (String) -> Boolean = { true }) {
        if (this@ViewModifier is StaticCellSizer) return

        (this as? ViewGroup)?.children
                ?.filterIsInstance<TextView>()
                ?.filter { filter(it.text.toString()) }
                ?.firstOrNull()
                ?.let { Log.i("TEST", "$action ${it.text}") }
    }
}

internal inline val ViewModifier.isHorizontal get() = orientation == RecyclerView.HORIZONTAL

internal inline val View.currentColumn: Int
    get() {
        val recyclerView = parentRecyclerView ?: return CellSizer.UNKNOWN
        val viewHolder = recyclerView.getChildViewHolder(this) ?: return CellSizer.UNKNOWN

        return viewHolder.layoutPosition
    }

internal inline val View.parentRecyclerView: RecyclerView?
    get() = parent as? RecyclerView

private inline var View.sizingRunnable: Runnable?
    get() = getTag(R.id.recyclerview_dynamic_sizing_handler) as? Runnable
    set(value) {
        (getTag(R.id.recyclerview_dynamic_sizing_handler) as? Runnable)?.let(this::removeCallbacks)
        setTag(R.id.recyclerview_dynamic_sizing_handler, value)
    }

internal inline val RecyclerView.isBusy get() = !isLaidOut || isLayoutRequested || isComputingLayout

private inline fun View.onParentIdle(crossinline action: () -> Unit) {
    val runnable = object : Runnable {
        override fun run() {
            val parent = parentRecyclerView
            if (parent != null && parent.isBusy) post(this)
            else {
                if (parent != null) action()
                sizingRunnable = null
            }
        }
    }

    sizingRunnable = runnable

    post(runnable)
    doOnDetach { it.sizingRunnable = null }
}

internal inline fun <T>  MutableCollection<T>.clear(afterRemove: (T) -> Unit) {
    val iterator = iterator()
    while (iterator.hasNext()) {
        val next = iterator.next()
        iterator.remove()
        afterRemove(next)
    }
}