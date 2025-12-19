package com.example.diplom

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.diplom.model.Landmark

class LandmarkAdapter(
    private var landmarks: List<Landmark>,
    private val onClick: (Landmark) -> Unit
) : RecyclerView.Adapter<LandmarkAdapter.LandmarkViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LandmarkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_landmark, parent, false)
        return LandmarkViewHolder(view)
    }

    override fun onBindViewHolder(holder: LandmarkViewHolder, position: Int) {
        val landmark = landmarks[position]
        holder.bind(landmark, onClick)
    }

    override fun getItemCount(): Int {
        return landmarks.size
    }

    fun updateData(newData: List<Landmark>) {
        landmarks = newData
        notifyDataSetChanged()
    }

    class LandmarkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.landmark_name)

        fun bind(landmark: Landmark, onClick: (Landmark) -> Unit) {
            nameTextView.text = landmark.name
            itemView.setOnClickListener {
                onClick(landmark)
            }
        }
    }
}
