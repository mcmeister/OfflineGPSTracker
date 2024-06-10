package com.example.offlinegpstracker

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

@Composable
fun <T> LiveData<T>.collectAsStateWithLifecycle(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
): State<T?> {
    val state = remember { mutableStateOf(value) }
    DisposableEffect(lifecycleOwner) {
        val observer = Observer<T> { t -> state.value = t }
        observe(lifecycleOwner, observer)
        onDispose { removeObserver(observer) }
    }
    return state
}
