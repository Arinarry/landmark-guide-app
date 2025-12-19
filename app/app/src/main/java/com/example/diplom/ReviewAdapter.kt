package com.example.diplom

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ReviewAdapter(
    private val reviews: List<Review>,
    private val onDeleteClick: (Int) -> Unit,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

    class ReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.reviewContent)
        val date: TextView = view.findViewById(R.id.reviewDate)
        val landmarkName: TextView = view.findViewById(R.id.reviewLandmark)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_comment)
        val itemView: View = view
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviews[position]
        holder.landmarkName.text = review.landmarkName
        holder.content.text = review.content
        holder.date.text = review.date

        holder.deleteButton.setOnClickListener {
            onDeleteClick(position)
        }

        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
    }

    override fun getItemCount(): Int = reviews.size
}
