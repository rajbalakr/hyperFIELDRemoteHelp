package com.hyperfield.remotehelp.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TableRow
import androidx.recyclerview.widget.RecyclerView
import com.opentok.accelerator.core.utils.MediaType
import com.hyperfield.remotehelp.R
import com.hyperfield.remotehelp.extension.hide
import com.hyperfield.remotehelp.extension.show
import com.hyperfield.remotehelp.ui.ParticipantsAdapter.ParticipantViewHolder

class ParticipantsAdapter(
    private val context: Context,
    private val participantsList: List<Participant>,
    private val participantAdapterListener: ParticipantAdapterListener
) : RecyclerView.Adapter<ParticipantViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParticipantViewHolder {
        val view = LayoutInflater.from(context).inflate(viewType, parent, false)
        return ParticipantViewHolder(view)
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.grid_item
    }

    override fun onBindViewHolder(holder: ParticipantViewHolder, position: Int) {
        val participant = participantsList[position]

        holder.container.removeAllViews()
        holder.id = participant.remoteId
        holder.type = participant.type
        holder.listener = participantAdapterListener

        val params = TableRow.LayoutParams(participant.containerSize.width, participant.containerSize.height)
        holder.container.layoutParams = params

        if (!participant.status.has(MediaType.VIDEO)
            || participant.type == Participant.Type.REMOTE
            && !participant.status.subscribedTo(MediaType.VIDEO)
        ) {
            holder.audiOnlyView.show()
            holder.container.addView(holder.audiOnlyView, params)
        } else {
            holder.audiOnlyView.hide()
            if (participant.status.view != null) {
                val parent = participant.status.view.parent as ViewGroup?
                parent?.removeView(participant.status.view)
                holder.container.addView(participant.status.view)
            }
        }
    }

    override fun getItemCount(): Int {
        return participantsList.size
    }

    interface ParticipantAdapterListener {
        fun mediaControlChanged(remoteId: String?)
    }

    inner class ParticipantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private var controls = view.findViewById(R.id.remoteControls) as RelativeLayout
        var audiOnlyView = view.findViewById(R.id.audioOnlyView) as RelativeLayout
        var container = view.findViewById(R.id.itemView) as RelativeLayout
        var id: String? = null
        var type: Participant.Type? = null

        var listener: ParticipantAdapterListener? = null
        init {
            view.setOnClickListener {
                if (type == Participant.Type.REMOTE) {
                    container.removeView(controls)
                    if (controls.visibility == View.GONE) {
                        controls.show()
                        container.addView(controls)
                    } else {
                        controls.hide()
                    }
                    listener?.mediaControlChanged(id)
                }
            }
        }
    }
}