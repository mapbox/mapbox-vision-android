package com.mapbox.vision.utils.listeners

import com.mapbox.vision.mobile.core.utils.delegate.DelegateWeakRef
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArraySet

interface CompositeListener<T> {
    fun addListener(listener: T)
    fun removeListener(listener: T)

    open class WeakRefImpl<T> : CompositeListener<T> {
        private val listeners = CopyOnWriteArraySet<WeakReference<T>>()

        override fun addListener(listener: T) {
            listeners.add(WeakReference(listener))
        }

        override fun removeListener(listener: T) {
            listeners.find { it.get() == listener }?.let { listeners.remove(it) }
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

fun <T> delegateWeakPropertyListener(
    compositeListener: CompositeListener<T>,
    initValue: T? = null
): DelegateWeakRef<T> =
    object : DelegateWeakRef<T>(initValue) {
        override fun onValueChange(oldValue: T?, newValue: T?) {
            oldValue?.let { compositeListener.removeListener(it) }
            newValue?.let { compositeListener.addListener(it) }
        }
    }
