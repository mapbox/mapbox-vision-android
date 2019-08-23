package com.mapbox.vision.dsl

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicTest

@DslMarker
annotation class VisionDslMarker

object TestCase {

    private val givenBlocks = mutableListOf<DynamicContainer>()
    private val givenBlockDisplayNames = mutableSetOf<String>()

    private val whenBlocks = mutableListOf<DynamicContainer>()
    private val whenBlockDisplayNames = mutableSetOf<String>()

    private val thenBlocks = mutableListOf<DynamicTest>()
    private val thenBlockDisplayNames = mutableSetOf<String>()

    operator fun invoke(block: TestContext.() -> Unit): List<DynamicContainer> {
        givenBlocks.clear()
        givenBlockDisplayNames.clear()
        TestContext().run(block)
        return givenBlocks
    }

    internal fun addGivenBlock(displayName: String, prefix: String = "Given") {
        val givenBlockDisplayName = getBlockDisplayName(displayName, prefix)
        addToSetOrThrowException(givenBlockDisplayName, givenBlockDisplayNames)

        givenBlocks.add(DynamicContainer.dynamicContainer(givenBlockDisplayName, whenBlocks.toMutableList()))
        whenBlocks.clear()
        whenBlockDisplayNames.clear()
    }

    internal fun addWhenBlock(displayName: String, prefix: String = "When") {
        val whenBlockDisplayName = getBlockDisplayName(displayName, prefix)
        addToSetOrThrowException(whenBlockDisplayName, whenBlockDisplayNames)

        whenBlocks.add(DynamicContainer.dynamicContainer(whenBlockDisplayName, thenBlocks.toMutableList()))
        thenBlocks.clear()
        thenBlockDisplayNames.clear()
    }

    internal fun addThenBlock(displayName: String, prefix: String = "Then", block: () -> Unit) {
        val thenBlockDisplayName = getBlockDisplayName(displayName, prefix)
        addToSetOrThrowException(thenBlockDisplayName, thenBlockDisplayNames)

        thenBlocks.add(DynamicTest.dynamicTest(thenBlockDisplayName, block))
    }

    private fun getBlockDisplayName(displayName: String, prefix: String) = "$prefix: $displayName"

    private fun addToSetOrThrowException(displayName: String, displayNameSet: MutableSet<String>) {
        if (displayName in displayNameSet) {
            throw Exception("Block with name <$displayName> already exists")
        }
        displayNameSet.add(displayName)
    }
}

@VisionDslMarker
class TestContext {
    fun Given(displayName: String, block: GivenContext.() -> Unit) {
        GivenContext().run(block)
        TestCase.addGivenBlock(displayName)
    }
}

@VisionDslMarker
class GivenContext {
    fun When(displayName: String, block: WhenContext.() -> Unit) {
        WhenContext().run(block)
        TestCase.addWhenBlock(displayName)
    }
}

@VisionDslMarker
class WhenContext {
    fun Then(displayName: String, block: () -> Unit) {
        TestCase.addThenBlock(displayName, block = block)
    }
}
