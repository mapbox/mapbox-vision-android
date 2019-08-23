package com.mapbox.vision.dsl

import io.mockk.MockKVerificationScope
import io.mockk.Ordering
import io.mockk.verify
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicTest

@DslMarker
annotation class VisionDslMarker

object TestCase {

    private const val GIVEN_PREFIX_NAME = "Given"
    private const val WHEN_PREFIX_NAME = "When"
    private const val THEN_PREFIX_NAME = "Then"
    private const val VERIFY_PREFIX_NAME = "Verify"

    internal enum class WhenPrefix(val prefixName: String) {
        WHEN(WHEN_PREFIX_NAME)
    }

    internal enum class ThenPrefix(val prefixName: String) {
        THEN(THEN_PREFIX_NAME),
        VERIFY(VERIFY_PREFIX_NAME)
    }

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

    internal fun addGivenBlock(displayName: String) {
        val givenBlockDisplayName = getBlockDisplayName(displayName, GIVEN_PREFIX_NAME)
        addToSetOrThrowException(givenBlockDisplayName, givenBlockDisplayNames)

        givenBlocks.add(DynamicContainer.dynamicContainer(givenBlockDisplayName, whenBlocks.toMutableList()))
        whenBlocks.clear()
        whenBlockDisplayNames.clear()
    }

    internal fun addWhenBlock(displayName: String, prefix: WhenPrefix = WhenPrefix.WHEN) {
        val whenBlockDisplayName = getBlockDisplayName(displayName, prefix.prefixName)
        addToSetOrThrowException(whenBlockDisplayName, whenBlockDisplayNames)

        whenBlocks.add(DynamicContainer.dynamicContainer(whenBlockDisplayName, thenBlocks.toMutableList()))
        thenBlocks.clear()
        thenBlockDisplayNames.clear()
    }

    internal fun addCheckBlock(displayName: String, prefix: ThenPrefix = TestCase.ThenPrefix.THEN, block: () -> Unit) {
        val thenBlockDisplayName = getBlockDisplayName(displayName, prefix.prefixName)
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
        TestCase.addCheckBlock(displayName, block = block)
    }

    fun Verify(
        displayName: String,
        ordering: Ordering = Ordering.UNORDERED,
        inverse: Boolean = false,
        atLeast: Int = 1,
        atMost: Int = Int.MAX_VALUE,
        exactly: Int = -1,
        timeout: Long = 0,
        verifyBlock: MockKVerificationScope.() -> Unit
    ) {
        TestCase.addCheckBlock(displayName, prefix = TestCase.ThenPrefix.VERIFY) {
            verify(
                ordering = ordering,
                inverse = inverse,
                atLeast = atLeast,
                atMost = atMost,
                exactly = exactly,
                timeout = timeout,
                verifyBlock = verifyBlock
            )
        }
    }
}
