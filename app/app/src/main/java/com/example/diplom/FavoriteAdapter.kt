package com.example.diplom

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.diplom.model.Landmark

data class FavoriteItem(val name: String, val description: String, val imageResId: Int)

class FavoriteAdapter(
    private var items: List<Landmark>,
    private val onFavoriteClick: (Landmark, Boolean) -> Unit // Boolean - новое состояние
) : RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder>() {

    class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.favoriteTitle)
        val description: TextView = itemView.findViewById(R.id.favoriteDescription)
        val image: ImageView = itemView.findViewById(R.id.favoriteImage)
        val favoriteButton: ImageButton = itemView.findViewById(R.id.favorite_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        holder.description.text = item.description
        Glide.with(holder.image.context)
            .load(item.imageUrl)
            .placeholder(R.drawable.placeholder)
            .into(holder.image)

        // Устанавливаем начальный цвет
        val isFavorite = FavoritesManager.isFavorite(item.id)
        updateHeartColor(holder.favoriteButton, isFavorite)

        holder.favoriteButton.setOnClickListener {
            val newFavoriteState = !FavoritesManager.isFavorite(item.id)
            updateHeartColor(holder.favoriteButton, newFavoriteState)
            onFavoriteClick(item, newFavoriteState)
        }

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, LandmarkDetailActivity::class.java).apply {
                putExtra("landmark", item)
            }
            context.startActivity(intent)
        }
    }

    private fun updateHeartColor(button: ImageButton, isFavorite: Boolean) {
        button.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                button.context,
                if (isFavorite) R.color.black else R.color.LightGray
            )
        )
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<Landmark>) {
        items = newItems
        notifyDataSetChanged()
    }

}