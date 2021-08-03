package com.arraystack.usi

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arraystack.usi.databinding.ActivityMainBinding
import com.arraystack.usi.databinding.ListElementBinding
import com.bumptech.glide.Glide

class Adapter(private val context: Context, val dataModel: ArrayList<DataModel>) :
    RecyclerView.Adapter<Adapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ListElementBinding.inflate(
                LayoutInflater.from(context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: Adapter.ViewHolder, position: Int) {
        holder.bind(context, dataModel[position])
    }

    override fun getItemCount(): Int {
        return dataModel.size
    }

    inner class ViewHolder(private val binding: ListElementBinding) :
        RecyclerView.ViewHolder(binding.root) {

        var item: DataModel? = null
        fun bind(context: Context, model: DataModel) {
            binding.txtTitle.text = model.title
            binding.txtDesc.text = model.description
            Glide.with(context)
                .load(model.imageLink)
                .into(binding.imgImage);
        }
    }
}