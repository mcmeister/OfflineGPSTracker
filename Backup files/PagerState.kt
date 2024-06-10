package com.example.offlinegpstracker

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PagerState(
    initialPage: Int = 0
) {
    var currentPage by mutableStateOf(initialPage)
        private set

    private val mutex = Mutex()

    suspend fun animateScrollToPage(
        page: Int,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = androidx.compose.animation.core.TweenSpec()
    ) {
        mutex.withLock {
            // Dummy animation logic, to be implemented as needed
            currentPage = page
        }
    }

    fun animateScrollToPage(
        scope: CoroutineScope,
        page: Int,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = androidx.compose.animation.core.TweenSpec()
    ) {
        scope.launch {
            animateScrollToPage(page, animationSpec)
        }
    }
}
