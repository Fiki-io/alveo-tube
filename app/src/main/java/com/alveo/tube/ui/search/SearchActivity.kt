package com.alveo.tube.ui.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.alveo.tube.core.extractor.SearchService
import com.alveo.tube.core.model.MediaItem
import com.alveo.tube.databinding.ActivitySearchBinding
import com.alveo.tube.ui.player.PlayerActivity
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val searchService = SearchService()
    private lateinit var searchAdapter: SearchAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupListeners()
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter { selectedItem ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_MEDIA_ITEM, selectedItem)
            }
            startActivity(intent)
        }

        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(this@SearchActivity)
            adapter = searchAdapter
        }
    }

    private fun setupListeners() {
        binding.etSearchQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        binding.btnSearch.setOnClickListener {
            performSearch()
        }

        binding.btnRetrySearch.setOnClickListener {
            performSearch()
        }
    }

    private fun performSearch() {
        val query = binding.etSearchQuery.text.toString().trim()
        if (query.isEmpty()) return

        hideKeyboard()
        showLoadingState()

        lifecycleScope.launch {
            val results = searchService.search(query)
            if (results.isNotEmpty()) {
                showResultsState(results)
            } else {
                showEmptyState()
            }
        }
    }

    private fun showLoadingState() {
        binding.layoutLoadingState.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE
        binding.layoutErrorState.visibility = View.GONE
        binding.rvSearchResults.visibility = View.GONE
    }

    private fun showResultsState(items: List<MediaItem>) {
        binding.layoutLoadingState.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.GONE
        binding.layoutErrorState.visibility = View.GONE
        binding.rvSearchResults.visibility = View.VISIBLE
        searchAdapter.submitList(items)
    }

    private fun showEmptyState() {
        binding.layoutLoadingState.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.VISIBLE
        binding.layoutErrorState.visibility = View.GONE
        binding.rvSearchResults.visibility = View.GONE
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(binding.etSearchQuery.windowToken, 0)
    }
}
