package com.example.diplom

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class CommentsAdapter(private val comments: List<Comment>) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.comment_item, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.bind(comment)
    }

    override fun getItemCount(): Int = comments.size

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userAvatar: ImageView = itemView.findViewById(R.id.profileImage)
        private val userName: TextView = itemView.findViewById(R.id.userName)
        private val commentText: TextView = itemView.findViewById(R.id.commentText)
        private val commentDate: TextView = itemView.findViewById(R.id.commentDate)

        fun bind(comment: Comment) {
            when (val avatar = comment.userProfileImage) {
                is Int -> Glide.with(itemView.context)
                    .load(avatar)
                    .circleCrop()
                    .into(userAvatar)
                is String -> Glide.with(itemView.context)
                    .load(avatar)
                    .circleCrop()
                    .placeholder(R.drawable.avatar2)
                    .into(userAvatar)
                is Uri -> Glide.with(itemView.context)
                    .load(avatar)
                    .circleCrop()
                    .placeholder(R.drawable.avatar2)
                    .into(userAvatar)
            }

            userName.text = comment.userName
            commentText.text = comment.commentText
            commentDate.text = comment.commentDate
        }
    }
}
