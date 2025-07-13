package com.empowerswr.test.ui.screens

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.empowerswr.test.network.Document
import com.empowerswr.test.R

class DocumentAdapter(
    private val documents: List<Document>,
    private val onClick: (Document) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
        val button: Button = view.findViewById(android.R.id.button1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val document = documents[position]
        holder.textView.text = document.name
        holder.button.setOnClickListener { onClick(document) }
    }

    override fun getItemCount(): Int = documents.size
}