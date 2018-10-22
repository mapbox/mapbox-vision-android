package com.mapbox.vision.performance

import com.mapbox.vision.core.CoreWrapper
import com.mapbox.vision.core.utils.snapdragon.SupportedSnapdragonBoards

internal interface PerformanceManager {

    fun setModelConfig(modelConfig: ModelPerformanceConfig)

    class SnapdragonImpl(
            private val coreWrapper: CoreWrapper,
            private val boardName: String
    ) : PerformanceManager {

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
                        SupportedSnapdragonBoards.SDM845.name -> Snapdragon_845
                        SupportedSnapdragonBoards.MSM8998.name -> Snapdragon_835
                        SupportedSnapdragonBoards.MSM8996.name -> Snapdragon_821
                        SupportedSnapdragonBoards.SDM710.name -> Snapdragon_710
                        SupportedSnapdragonBoards.SDM660.name -> Snapdragon_660
                        else -> Snapdragon_660 // default other 6xx to low performance
                    }
                }

                fun getMinWorkingFps(): ModelsFps = MINIMUM_SUPPORTED_WORKING_FPS

                fun getMinBackgroundFps(): ModelsFps = MINIMUM_SUPPORTED_BACKGROUND_FPS

                fun getMaxFps(model: ModelPerformance): ModelsFps =
                        when (model) {
                            ModelPerformance.Off -> {
                                MINIMUM_SUPPORTED_BACKGROUND_FPS
                            }
                            is ModelPerformance.On -> {
                                when (model.rate) {
                                    ModelPerformanceRate.LOW -> MINIMUM_SUPPORTED_WORKING_FPS
                                    ModelPerformanceRate.MEDIUM -> average(MINIMUM_SUPPORTED_WORKING_FPS, maxSupportedFps)
                                    ModelPerformanceRate.HIGH -> maxSupportedFps
                                }

                            }
                        }

                object Snapdragon_845 : SnpeBoard(
                        maxSupportedFps = ModelsFps(
                                detectionFps = Fps(30f),
                                segmentationFps = Fps(18f),
                                mergedFpsRange = Fps(14f)
                        )
                )

                object Snapdragon_835 : SnpeBoard(
                        maxSupportedFps = ModelsFps(
                                detectionFps = Fps(20f),
                                segmentationFps = Fps(13f),
                                mergedFpsRange = Fps(9f)
                        )
                )

                object Snapdragon_821 : SnpeBoard(
                        maxSupportedFps = ModelsFps(
                                detectionFps = Fps(13f),
                                segmentationFps = Fps(6f),
                                mergedFpsRange = Fps(4f)
                        )
                )

                object Snapdragon_710 : SnpeBoard(
                        maxSupportedFps = ModelsFps(
                                detectionFps = Fps(20f),
                                segmentationFps = Fps(9f),
                                mergedFpsRange = Fps(7f)
                        )
                )

                object Snapdragon_660 : SnpeBoard(
                        maxSupportedFps = ModelsFps(
                                detectionFps = Fps(13f),
                                segmentationFps = Fps(6f),
                                mergedFpsRange = Fps(4f)
                        )
                )
            }
        }

        private val snpeBoard = SnpeBoard.fromBoardName(boardName.toUpperCase())

        override fun setModelConfig(modelConfig: ModelPerformanceConfig) {
            when (modelConfig) {
                is ModelPerformanceConfig.Merged -> {
                    setDetectionPerformance(
                            modelConfig.performance,
                            minFps = snpeBoard.getMinWorkingFps().mergedFpsRange,
                            maxFps = snpeBoard.getMaxFps(modelConfig.performance).mergedFpsRange,
                            backgroundFps = snpeBoard.getMinBackgroundFps().mergedFpsRange
                    )
                    setSegmentationPerformance(
                            modelConfig.performance,
                            minFps = snpeBoard.getMinWorkingFps().mergedFpsRange,
                            maxFps = snpeBoard.getMaxFps(modelConfig.performance).mergedFpsRange,
                            backgroundFps = snpeBoard.getMinBackgroundFps().mergedFpsRange
                    )
                }
                is ModelPerformanceConfig.Separate -> {
                    setDetectionPerformance(
                            modelConfig.detectionPerformance,
                            minFps = snpeBoard.getMinWorkingFps().detectionFps,
                            maxFps = snpeBoard.getMaxFps(modelConfig.detectionPerformance).detectionFps,
                            backgroundFps = snpeBoard.getMinBackgroundFps().detectionFps
                    )
                    setSegmentationPerformance(
                            modelConfig.segmentationPerformance,
                            minFps = snpeBoard.getMinWorkingFps().segmentationFps,
                            maxFps = snpeBoard.getMaxFps(modelConfig.segmentationPerformance).segmentationFps,
                            backgroundFps = snpeBoard.getMinBackgroundFps().segmentationFps
                    )
                }
            }
        }

        private fun setDetectionPerformance(
                modelPerformance: ModelPerformance,
                minFps: Fps,
                maxFps: Fps,
                backgroundFps: Fps
        ) {
            when (modelPerformance) {
                is ModelPerformance.On -> {
                    when (modelPerformance.mode) {
                        ModelPerformanceMode.FIXED -> {
                            coreWrapper.setDetectionFixedFps(maxFps.value)
                        }
                        ModelPerformanceMode.DYNAMIC -> {
                            coreWrapper.setDetectionDynamicFps(minFps.value, maxFps.value)
                        }
                    }
                }
                ModelPerformance.Off -> {
                    coreWrapper.setDetectionFixedFps(backgroundFps.value)
                }
            }
        }

        private fun setSegmentationPerformance(
                modelPerformance: ModelPerformance,
                minFps: Fps,
                maxFps: Fps,
                backgroundFps: Fps
        ) {
            when (modelPerformance) {
                is ModelPerformance.On -> {
                    when (modelPerformance.mode) {
                        ModelPerformanceMode.FIXED -> {
                            coreWrapper.setSegmentationFixedFps(maxFps.value)
                        }
                        ModelPerformanceMode.DYNAMIC -> {
                            coreWrapper.setSegmentationDynamicFps(
                                    minFps = minFps.value,
                                    maxFps = maxFps.value
                            )
                        }
                    }
                }
                ModelPerformance.Off -> {
                    coreWrapper.setSegmentationFixedFps(backgroundFps.value)
                }
            }
        }
    }
}
