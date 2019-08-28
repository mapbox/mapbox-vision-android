package com.mapbox.vision.utils.observable

import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArraySet

open class ObserverComposer<T> : Observable<T> {
    private val set = CopyOnWriteArraySet<WeakReference<T>>()
    private var needClean = false

    override fun addObservable(observer: T) {
        set.add(WeakReference(observer))
    }

    override fun removeObserver(observer: T) {
        set.find { it.get() == observer }?.let { set.remove(it) }
    }

    protected fun forEach(func: T.() -> Unit) {
        set.forEach { weak ->
            val instance = weak.get()
            if (instance != null) {
                instance.func()
            } else {
                needClean = true
            }
        }
        cleanIfNeed()
    }

    private fun cleanIfNeed() {
        if (needClean) {
            set.removeAll { it.get() == null }
            needClean = false
        }
    }
}
