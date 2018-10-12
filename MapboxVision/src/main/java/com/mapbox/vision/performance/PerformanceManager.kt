package com.mapbox.vision.performance

import android.os.Build
import com.mapbox.vision.core.CoreWrapper
import com.mapbox.vision.core.utils.snapdragon.SupportedSnapdragonBoards

internal interface PerformanceManager {

    fun setModelConfig(modelConfig: ModelPerformanceConfig)

    class SnapdragonImpl(private val coreWrapper: CoreWrapper) : PerformanceManager {

        companion object {

            internal data class Fps(val value: Float)

            internal data class ModelsFps(
                    val detectionFps: Fps,
                    val segmentationFps: Fps,
                    val mergedFpsRange: Fps
            )

            private fun average(first: ModelsFps, second: ModelsFps) = ModelsFps(
                    detectionFps = Fps(
                            (first.detectionFps.value + second.detectionFps.value) / 2
                    ),
                    segmentationFps = Fps(
                            (first.segmentationFps.value + second.segmentationFps.value) / 2
                    ),
                    mergedFpsRange = Fps(
                            (first.mergedFpsRange.value + second.mergedFpsRange.value) / 2
                    )
            )

            // The minimum supported FPS our ML models can deal with.
            private val MINIMUM_SUPPORTED_WORKING_FPS = ModelsFps(
                    detectionFps = Fps(4f),
                    segmentationFps = Fps(2f),
                    mergedFpsRange = Fps(2f)
            )

            // The minimum FPS for background - used instead of complete shutdown
            private val MINIMUM_SUPPORTED_BACKGROUND_FPS = ModelsFps(
                    detectionFps = Fps(3f),
                    segmentationFps = Fps(1f),
                    mergedFpsRange = Fps(1f)
            )

            internal sealed class SnpeBoard(
                    private val maxSupportedFps: ModelsFps
            ) {
                companion object {
                    fun fromBoardName(boardName: String): SnpeBoard = when (boardName) {
                        SupportedSnapdragonBoards.SDM845.name -> SDM854
                        SupportedSnapdragonBoards.MSM8998.name -> MSM8998
                        SupportedSnapdragonBoards.MSM8996.name -> MSM8996
                        SupportedSnapdragonBoards.SDM710.name -> SDM710
                        SupportedSnapdragonBoards.SDM660.name -> SDM660
                        else -> SDM660 // default other 6xx to low performance
                    }
                }

                fun getMinWorkingFps(): ModelsFps = MINIMUM_SUPPORTED_WORKING_FPS

                fun getMinBackgroundFps(): ModelsFps = MINIMUM_SUPPORTED_BACKGROUND_FPS

                fun getMaxFps(rate: ModelPerformanceRate): ModelsFps =
                        when (rate) {
                            ModelPerformanceRate.LOW -> MINIMUM_SUPPORTED_WORKING_FPS
                            ModelPerformanceRate.MEDIUM -> average(MINIMUM_SUPPORTED_WORKING_FPS, maxSupportedFps)
                            ModelPerformanceRate.HIGH -> maxSupportedFps
                        }

                object SDM854 : SnpeBoard(
                        maxSupportedFps = ModelsFps(
                                detectionFps = Fps(24f),
                                segmentationFps = Fps(14f),
                                mergedFpsRange = Fps(11f)
                        )
                )

                object MSM8998 : SnpeBoard(
                        maxSupportedFps = ModelsFps(
                                detectionFps = Fps(15f),
                                segmentationFps = Fps(10f),
                                mergedFpsRange = Fps(7f)
                        )
                )

                object MSM8996 : SnpeBoard(
                        maxSupportedFps = ModelsFps(
                                detectionFps = Fps(11f),
                                segmentationFps = Fps(5f),
                                mergedFpsRange = Fps(3f)
                        )
                )

                object SDM710 : SnpeBoard(
                        maxSupportedFps = ModelsFps(
                                detectionFps = Fps(15f),
                                segmentationFps = Fps(6f),
                                mergedFpsRange = Fps(5f)
                        )
                )

                object SDM660 : SnpeBoard(
                        maxSupportedFps = ModelsFps(
                                detectionFps = Fps(12f),
                                segmentationFps = Fps(4f),
                                mergedFpsRange = Fps(3f)
                        )
                )
            }
        }

        private val snpeChip = SnpeBoard.fromBoardName(Build.BOARD.toUpperCase())

        override fun setModelConfig(modelConfig: ModelPerformanceConfig) {
            when (modelConfig) {
                is ModelPerformanceConfig.Merged -> {
                    setDetectionPerformance(modelConfig.performance)
                    setSegmentationPerformance(modelConfig.performance)
                }
                is ModelPerformanceConfig.Separate -> {
                    setDetectionPerformance(modelConfig.detectionPerformance)
                    setSegmentationPerformance(modelConfig.segmentationPerformance)
                }
            }
        }

        private fun setDetectionPerformance(modelPerformance: ModelPerformance) {
            when (modelPerformance) {
                is ModelPerformance.On -> {
                    when (modelPerformance.mode) {
                        ModelPerformanceMode.FIXED -> {
                            coreWrapper.setDetectionFixedFps(
                                    detectionFps = snpeChip.getMaxFps(modelPerformance.rate).detectionFps.value
                            )
                        }
                        ModelPerformanceMode.DYNAMIC -> {
                            coreWrapper.setDetectionDynamicFps(
                                    minFps = snpeChip.getMinWorkingFps().detectionFps.value,
                                    maxFps = snpeChip.getMaxFps(modelPerformance.rate).detectionFps.value
                            )
                        }
                    }
                }
                ModelPerformance.Off -> {
                    coreWrapper.setDetectionFixedFps(snpeChip.getMinBackgroundFps().detectionFps.value)
                }
            }
        }

        private fun setSegmentationPerformance(modelPerformance: ModelPerformance) {
            when (modelPerformance) {

                is ModelPerformance.On -> {
                    when (modelPerformance.mode) {
                        ModelPerformanceMode.FIXED -> {
                            coreWrapper.setSegmentationFixedFps(
                                    segmentationFps = snpeChip.getMaxFps(modelPerformance.rate).segmentationFps.value
                            )
                        }
                        ModelPerformanceMode.DYNAMIC -> {
                            coreWrapper.setSegmentationDynamicFps(
                                    minFps = snpeChip.getMinWorkingFps().segmentationFps.value,
                                    maxFps = snpeChip.getMaxFps(modelPerformance.rate).segmentationFps.value
                            )
                        }
                    }
                }
                ModelPerformance.Off -> {
                    coreWrapper.setSegmentationFixedFps(
                            segmentationFps = snpeChip.getMinBackgroundFps().segmentationFps.value
                    )
                }
            }
        }
    }
}
