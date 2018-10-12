package com.mapbox.vision.performancemanager

import android.os.Build
import com.mapbox.vision.core.CoreWrapper
import com.mapbox.vision.core.utils.snapdragon.SupportedSnapdragonBoards
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import java.util.*

internal class JNICoreSnapdragonPerformanceManager(private val coreWrapper: CoreWrapper) : PerformanceManager {

    private val highEndPerformanceMap = mapOf(
            Pair(ModelPerformanceRate.LOW, PerformanceRange(FPSRange(4f, arrayOf(4f, 4f)), FPSRange(2f, arrayOf(2f, 2f)))),
            Pair(ModelPerformanceRate.MEDIUM, PerformanceRange(FPSRange(9f, arrayOf(4f, 9f)), FPSRange(5.5f, arrayOf(2f, 5.5f)))),
            Pair(ModelPerformanceRate.HIGH, PerformanceRange(FPSRange(15f, arrayOf(4f, 15f)), FPSRange(8f, arrayOf(2f, 8f))))
    )

    private val midEndPerformanceMap = mapOf(
            Pair(ModelPerformanceRate.LOW, PerformanceRange(FPSRange(4f, arrayOf(4f, 4f)), FPSRange(2f, arrayOf(2f, 2f)))),
            Pair(ModelPerformanceRate.MEDIUM, PerformanceRange(FPSRange(8f, arrayOf(4f, 8f)), FPSRange(4.5f, arrayOf(2f, 4.5f)))),
            Pair(ModelPerformanceRate.HIGH, PerformanceRange(FPSRange(12f, arrayOf(4f, 12f)), FPSRange(7f, arrayOf(2f, 7f))))
    )

    private val lowEndPerformanceMap = mapOf(
            Pair(ModelPerformanceRate.LOW, PerformanceRange(FPSRange(4f, arrayOf(4f, 4f)), FPSRange(2f, arrayOf(2f, 2f)))),
            Pair(ModelPerformanceRate.MEDIUM, PerformanceRange(FPSRange(8f, arrayOf(4f, 8f)), FPSRange(4.5f, arrayOf(2f, 4.5f)))),
            Pair(ModelPerformanceRate.HIGH, PerformanceRange(FPSRange(12f, arrayOf(4f, 12f)), FPSRange(7f, arrayOf(2f, 7f))))
    )

    private val snapdragonPerformanceMap = mapOf(
            // Snapdragon 845
            Pair(SupportedSnapdragonBoards.SDM845.name, highEndPerformanceMap),
            // Snapdragon 835
            Pair(SupportedSnapdragonBoards.MSM8998.name, highEndPerformanceMap),
            // Snapdragon 820
            Pair(SupportedSnapdragonBoards.MSM8996.name, midEndPerformanceMap),
            // Snapdragon 710
            Pair(SupportedSnapdragonBoards.SDM710.name, midEndPerformanceMap),
            // Snapdragon 660
            Pair(SupportedSnapdragonBoards.SDM660.name, lowEndPerformanceMap),
            // Snapdragon 650
            Pair(SupportedSnapdragonBoards.MSM8956.name, lowEndPerformanceMap),
            Pair(SupportedSnapdragonBoards.MSM8952.name, lowEndPerformanceMap)
    )

    private val performanceMap: Map<ModelPerformanceRate, PerformanceRange> = snapdragonPerformanceMap[Build.BOARD.toUpperCase()]!!

    override fun setDetectionPerformance(modelPerformance: ModelPerformance) {

        when (modelPerformance.mode) {
            ModelPerformanceMode.FIXED -> {
                coreWrapper.setDetectionFixedFps(performanceMap[modelPerformance.rate]!!.detectionFPSRange.fixedValue.toFloat())
            }
            ModelPerformanceMode.DYNAMIC -> {
                val dynamicFpsRange = performanceMap[modelPerformance.rate]!!.detectionFPSRange.dynamicRange
                coreWrapper.setDetectionDynamicFps(dynamicFpsRange[0], dynamicFpsRange[1])
            }
        }
    }

    override fun setSegmentationPerformance(modelPerformance: ModelPerformance) {
        when (modelPerformance.mode) {
            ModelPerformanceMode.FIXED -> {
                coreWrapper.setSegmentationFixedFps(performanceMap[modelPerformance.rate]!!.segmentationFPSRange.fixedValue.toFloat())
            }
            ModelPerformanceMode.DYNAMIC -> {
                val dynamicFpsRange = performanceMap[modelPerformance.rate]!!.segmentationFPSRange.dynamicRange
                coreWrapper.setSegmentationDynamicFps(dynamicFpsRange[0], dynamicFpsRange[1])
            }
        }
    }

    internal data class FPSRange(val fixedValue: Float, val dynamicRange: Array<Float>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as FPSRange

            if (fixedValue != other.fixedValue) return false
            if (!Arrays.equals(dynamicRange, other.dynamicRange)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fixedValue.hashCode()
            result = 31 * result + Arrays.hashCode(dynamicRange)
            return result
        }
    }

    internal data class PerformanceRange(val detectionFPSRange: FPSRange, val segmentationFPSRange: FPSRange)

}