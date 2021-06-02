package com.example.foodyapp.ui.fragments.recipes.bottomsheet

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.foodyapp.databinding.RecipesBottomSheetBinding
import com.example.foodyapp.databinding.RecipesRowLayoutBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class RecipesBottomSheet : BottomSheetDialogFragment() {

    private var _binding : RecipesBottomSheetBinding? = null
    private val binding get() =  _binding!!


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = RecipesBottomSheetBinding.inflate(layoutInflater, container, false)
        return binding.root
    }
}