package com.example.diplom.model

import android.os.Parcel
import android.os.Parcelable

data class Landmark(
    val id: Int,
    val name: String,
    val description: String = "",
    val imageUrl: String = "",
    val location: String = "",
    val coordinates: String = "",
    val tag: String = ""
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(name)
        parcel.writeString(description)
        parcel.writeString(imageUrl)
        parcel.writeString(location)
        parcel.writeString(coordinates)
        parcel.writeString(tag)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Landmark> {
        override fun createFromParcel(parcel: Parcel): Landmark {
            return Landmark(parcel)
        }

        override fun newArray(size: Int): Array<Landmark?> {
            return arrayOfNulls(size)
        }
    }

}
