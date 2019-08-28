package com.mapbox.vision.utils.observable

import com.mapbox.vision.mobile.core.utils.delegate.DelegateWeakRef

interface Observable<T> {
    fun addObservable(observer: T)
    fun removeObserver(observer: T)
}

fun <T> delegateWeakPropertyObservable(
    observable: Observable<T>,
    initValue: T? = null
): DelegateWeakRef<T> =
    object : DelegateWeakRef<T>(initValue) {
        override fun onValueChange(oldValue: T?, newValue: T?) {
            oldValue?.let { observable.removeObserver(it) }
            newValue?.let { observable.addObservable(it) }
        }
    }
