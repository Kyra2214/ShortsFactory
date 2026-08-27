package com.shortsfactory.viewmodels

import androidx.lifecycle.ViewModel
import com.shortsfactory.data.repository.ProjectRepository
import com.shortsfactory.domain.trends.TrendAnalyzer
import com.shortsfactory.domain.trends.TrendSearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val projectRepository: ProjectRepository,
    val trendSearchRepository: TrendSearchRepository,
    val trendAnalyzer: TrendAnalyzer
) : ViewModel()
