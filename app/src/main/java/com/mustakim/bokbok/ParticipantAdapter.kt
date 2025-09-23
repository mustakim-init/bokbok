package com.mustakim.bokbok

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class ParticipantAdapter(context: Context, items: MutableList<String>) :
    ArrayAdapter<String>(context, 0, items) {

    private val inflater = LayoutInflater.from(context)
    private val list = items

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val v = convertView ?: inflater.inflate(R.layout.item_participant, parent, false)
        val tv = v.findViewById<TextView>(R.id.participantName)
        val iv = v.findViewById<ImageView>(R.id.micIcon)
        val id = list[position]
        tv.text = id.take(8) // show short id
        iv.setImageResource(android.R.drawable.presence_audio_online)
        return v
    }
}
