package com.mapbox.vision.utils.observable

import com.mapbox.vision.mobile.core.utils.delegate.DelegateWeakRef
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArraySet

interface CompositeListener<T> {
    fun addListener(observer: T)
    fun removeListener(observer: T)

    open class Impl<T> : CompositeListener<T> {
        private val listeners = CopyOnWriteArraySet<WeakReference<T>>()

        override fun addListener(observer: T) {
            listeners.add(WeakReference(observer))
        }

        override fun removeListener(observer: T) {
            listeners.find { it.get() == observer }?.let { listeners.remove(it) }
        }

        protected fun forEach(func: T.() -> Unit) {
            listeners.forEach { weak ->
                val instance = weak.get()
                if (instance != null) {
                    instance.func()
                } else {
                    listeners.remove(weak)
                }
            }
        }
    }
}

fun <T> delegateWeakPropertyObservable(
    compositeListener: CompositeListener<T>,
    initValue: T? = null
): DelegateWeakRef<T> =
    object : DelegateWeakRef<T>(initValue) {
        override fun onValueChange(oldValue: T?, newValue: T?) {
            oldValue?.let { compositeListener.removeListener(it) }
            newValue?.let { compositeListener.addListener(it) }
        }
    }
