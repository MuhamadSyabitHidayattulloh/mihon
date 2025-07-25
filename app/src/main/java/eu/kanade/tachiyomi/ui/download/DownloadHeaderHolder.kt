package eu.kanade.tachiyomi.ui.download

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.viewholders.ExpandableViewHolder
import eu.kanade.tachiyomi.databinding.DownloadHeaderBinding

class DownloadHeaderHolder(view: View, adapter: FlexibleAdapter<*>) : ExpandableViewHolder(view, adapter) {

    private val binding = DownloadHeaderBinding.bind(view)

    @SuppressLint("SetTextI18n")
    fun bind(item: DownloadHeaderItem) {
        setDragHandleView(binding.reorder)
        val percent = (item.progress * 100).toInt()
        binding.title.text = if (percent in 1..99) {
            "${item.name} ($percent%) (${item.size})"
        } else {
            "${item.name} (${item.size})"
        }
    }

    override fun onActionStateChanged(position: Int, actionState: Int) {
        super.onActionStateChanged(position, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            binding.container.isDragged = true
            mAdapter.collapseAll()
        }
    }

    override fun onItemReleased(position: Int) {
        super.onItemReleased(position)
        binding.container.isDragged = false
        mAdapter.expandAll()
        (mAdapter as DownloadAdapter).downloadItemListener.onItemReleased(position)
    }
}
