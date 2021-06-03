package com.example.foodyapp.ui.fragments.recipes

import android.app.Application
import android.content.Intent
import android.net.Network
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.foodyapp.R
import com.example.foodyapp.viewmodels.MainViewModel
import com.example.foodyapp.adapters.RecipesAdapter
import com.example.foodyapp.databinding.FragmentRecipesBinding
import com.example.foodyapp.ui.fragments.recipes.bottomsheet.RecipesBottomSheet
import com.example.foodyapp.util.Constants.Companion.API_KEY
import com.example.foodyapp.util.NetworkListener
import com.example.foodyapp.util.NetworkResult
import com.example.foodyapp.util.observeOnce
import com.example.foodyapp.viewmodels.RecipesViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@ExperimentalCoroutinesApi
@AndroidEntryPoint

// SerchViewはandroidxの方をimportしないと動かない
class RecipesFragment : Fragment(), SearchView.OnQueryTextListener {

    private var _binding : FragmentRecipesBinding? = null
    private val  binding  get() = _binding!!
    private val  mAdapter  by lazy { RecipesAdapter() }
    private lateinit var mainViewModel : MainViewModel
    private lateinit var recipesViewModel: RecipesViewModel
    private val args by navArgs<RecipesFragmentArgs>()
    private lateinit var networkListener: NetworkListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ViewModelの取得
        mainViewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)

        // applicationの代わりに、(requireActivity()).get(MainViewModel::class.java)
        recipesViewModel = ViewModelProvider(requireActivity()).get(RecipesViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentRecipesBinding.inflate(layoutInflater, container, false)
        // Livedataを使用する際に以下を定義する必要がある
        binding.lifecycleOwner = this
        binding.mainViewModel = mainViewModel

        // 記述し忘れていた
        setHasOptionsMenu(true)

        setUpRecyclerView()

        recipesViewModel.readBackOnline.observe(viewLifecycleOwner, {
            recipesViewModel.backOnline = it
        })

        // これは他のアプリでも使える
        // ネットワークが使える状態になったときにtrueとなる
        lifecycleScope.launch {
            networkListener = NetworkListener()
            networkListener.checkNetworkAvailability(requireContext())
                .collect { status ->
                    Log.d("NetworkListener", status.toString())
                    recipesViewModel.networkStatus = status
                    recipesViewModel.showNetworkStatus()
                    readDatabase()
                }
        }

        binding.recipesFab.setOnClickListener {
            if(recipesViewModel.networkStatus){
                findNavController().navigate(R.id.action_recipesFragment_to_recipesBottomSheet)
            } else {
                recipesViewModel.showNetworkStatus()
            }
        }
        return binding.root
    }

    private fun setUpRecyclerView(){
        binding.shimmerRecyclerView.adapter = mAdapter
        binding.shimmerRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        showShimmerEffect()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.recipes_menu, menu)
        val search = menu.findItem(R.id.menu_search)
        val searchView = search.actionView as? SearchView
        searchView?.isSubmitButtonEnabled = true
        searchView?.setOnQueryTextListener(this)
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        Log.d("searchText", "query : ${query}")
        if (query != null) {
            searchApiData(query)
        }
        return true
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return true
    }


    // データベースにデータが存在する場合は
    private fun readDatabase() {
            // 非同期処理の範囲を制限するための機能
            // ライフサイクルに依存できて、launchの場合はonDestroyの際に処理が行われない
            // https://qiita.com/sokume2106/items/628d5c0296a56d47d5f3
            // lifecycleScope.launch {の位置がおかしかったため、2回ボタンを押さないと反映されないようになっていた
            lifecycleScope.launch {
                // Fragmentではthisを使用するとパフォーマンスが低下してしまう可能性があるため、
                // viewLifecycleOwnerを使用する

                // readRecipesはLiveDataであるため、情報が更新された場合にobserve以降の処理が行われる
                // アプリインストール時に、requestとreadの両方が呼び出されてしまうため、
                // extension functionのonserveOnceを使って、処理が一度しか行われないようにしている
                mainViewModel.readRecipes.observeOnce(viewLifecycleOwner, {database ->
                if (database.isNotEmpty() && !args.backFromBottomSheet) {
                    Log.d("RecipesFragment", "read databse called!")
                    // 2つのリストを比較してその差分を算出する
                    mAdapter.setData(database[0].foodRecipe)
                    hideShimmerEffect()
                } else {
                    requestApiData()
                }
            })
        }
    }

    // FoodAPIからデータを取得する
    private fun requestApiData(){
        Log.d("RecipesFragment", "requestAPI data called!")
        mainViewModel.getRecipes(recipesViewModel.applyQueries())
        mainViewModel.recipesResponse.observe(viewLifecycleOwner, { response ->

            when (response) {
                is NetworkResult.Success -> {
                    hideShimmerEffect()
                    response.data?.let {
                        mAdapter.setData(it)
                    }
                }
                is NetworkResult.Error -> {
                    hideShimmerEffect()
                    loadDataFromCache()
                    // response.dataが空の場合は、以下のトーストを表示しないように実装されていた
                    Toast.makeText(requireContext(), response.message.toString(), Toast.LENGTH_SHORT).show()
                }
                is NetworkResult.Loading -> {
                    showShimmerEffect()
                }
            }
        })
    }

    private fun searchApiData(searchQuery: String) {
        showShimmerEffect()
        mainViewModel.searchRecipes(recipesViewModel.applySearchQuery(searchQuery))
        mainViewModel.searchedRecipesResponse.observe(viewLifecycleOwner, { response ->
            when (response) {
                is NetworkResult.Success -> {
                    hideShimmerEffect()
                    val foodRecipe = response.data
                    foodRecipe?.let {
                        mAdapter.setData(it)
                    }
                }
                is NetworkResult.Error -> {
                    hideShimmerEffect()
                    loadDataFromCache()
                    Toast.makeText(requireContext(), response.message.toString(), Toast.LENGTH_SHORT).show()
                }
                is NetworkResult.Loading -> {
                    showShimmerEffect()
                }
            }

        })
    }

    private fun loadDataFromCache(){
        lifecycleScope.launch {
            mainViewModel.readRecipes.observe(viewLifecycleOwner, { database ->
                if(database.isNotEmpty()) {
                    mAdapter.setData(database[0].foodRecipe)
                }
            })
        }
    }

    private fun showShimmerEffect(){
        binding.shimmerRecyclerView.showShimmer()
    }

    private fun hideShimmerEffect(){
        binding.shimmerRecyclerView.hideShimmer()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }


}