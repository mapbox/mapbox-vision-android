package com.mapbox.vision.dsl

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest

@DslMarker
annotation class VisionDslMarker

object TestCase {

    private val givenBlocks = mutableListOf<DynamicNode>()
    private val whenBlocks = mutableListOf<DynamicNode>()
    private val thenBlocks = mutableListOf<DynamicTest>()

    operator fun invoke(init: TestContext.() -> Unit): List<DynamicNode> {
        givenBlocks.clear()
        TestContext().run(init)
        return givenBlocks
    }

    internal fun addGivenBlock(displayName: String) {
        givenBlocks.add(DynamicContainer.dynamicContainer("Given: $displayName", whenBlocks.toMutableList()))
        whenBlocks.clear()
    }

    internal fun addWhenBlock(displayName: String) {
        whenBlocks.add(DynamicContainer.dynamicContainer("When: $displayName", thenBlocks.toMutableList()))
        thenBlocks.clear()
    }

    internal fun addThenBlock(displayName: String, block: () -> Unit) {
        thenBlocks.add(DynamicTest.dynamicTest("Then: $displayName", block))
    }
}

@VisionDslMarker
class TestContext {
    fun Given(displayName: String, init: GivenContext.() -> Unit) {
        GivenContext().run(init)
        TestCase.addGivenBlock(displayName)
    }
}

@VisionDslMarker
class GivenContext {

    fun When(displayName: String, init: WhenContext.() -> Unit) {
        WhenContext().run(init)
        TestCase.addWhenBlock(displayName)
    }
}

@VisionDslMarker
class WhenContext {
    fun Then(displayName: String, block: () -> Unit) {
        TestCase.addThenBlock(displayName, block)
    }
}
