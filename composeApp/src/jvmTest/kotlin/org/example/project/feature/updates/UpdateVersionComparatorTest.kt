package org.example.project.feature.updates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateVersionComparatorTest {

    // --- isNewer ---

    @Test
    fun `same version is not newer`() {
        assertFalse(UpdateVersionComparator.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun `newer patch version is detected`() {
        assertTrue(UpdateVersionComparator.isNewer("1.0.0", "1.0.1"))
    }

    @Test
    fun `older version is not newer`() {
        assertFalse(UpdateVersionComparator.isNewer("1.0.1", "1.0.0"))
    }

    @Test
    fun `newer minor version is detected`() {
        assertTrue(UpdateVersionComparator.isNewer("1.0.0", "1.1.0"))
    }

    @Test
    fun `newer major version is detected`() {
        assertTrue(UpdateVersionComparator.isNewer("1.9.9", "2.0.0"))
    }

    @Test
    fun `older major version is not newer`() {
        assertFalse(UpdateVersionComparator.isNewer("2.0.0", "1.9.9"))
    }

    // --- v/V prefix handling ---

    @Test
    fun `lowercase v prefix is stripped - versions are equal`() {
        assertEquals(0, UpdateVersionComparator.compare("v1.0.0", "1.0.0"))
    }

    @Test
    fun `uppercase V prefix is stripped - versions are equal`() {
        assertEquals(0, UpdateVersionComparator.compare("V1.0.0", "1.0.0"))
    }

    @Test
    fun `both with v prefix are equal`() {
        assertEquals(0, UpdateVersionComparator.compare("v1.2.3", "v1.2.3"))
    }

    @Test
    fun `v prefix with newer version detected`() {
        assertTrue(UpdateVersionComparator.isNewer("v1.0.0", "v1.0.1"))
    }

    // --- Different component lengths ---

    @Test
    fun `missing components treated as zero - equal`() {
        assertEquals(0, UpdateVersionComparator.compare("1.0", "1.0.0"))
    }

    @Test
    fun `missing components treated as zero - shorter is older when longer has nonzero tail`() {
        assertTrue(UpdateVersionComparator.isNewer("1.0", "1.0.1"))
    }

    @Test
    fun `single component vs triple`() {
        assertEquals(0, UpdateVersionComparator.compare("1", "1.0.0"))
    }

    @Test
    fun `four components compared correctly`() {
        assertTrue(UpdateVersionComparator.isNewer("1.0.0.0", "1.0.0.1"))
    }

    // --- compare return values ---

    @Test
    fun `compare returns 0 for identical versions`() {
        assertEquals(0, UpdateVersionComparator.compare("2.3.4", "2.3.4"))
    }

    @Test
    fun `compare returns negative when current is older`() {
        assertTrue(UpdateVersionComparator.compare("1.0.0", "2.0.0") < 0)
    }

    @Test
    fun `compare returns positive when current is newer`() {
        assertTrue(UpdateVersionComparator.compare("2.0.0", "1.0.0") > 0)
    }

    // --- Non-numeric garbage ---

    @Test
    fun `non-numeric string normalizes to zero`() {
        assertEquals(0, UpdateVersionComparator.compare("abc", "xyz"))
    }

    @Test
    fun `garbage current vs real version - real is newer`() {
        assertTrue(UpdateVersionComparator.isNewer("abc", "1.0.0"))
    }

    @Test
    fun `real version vs garbage - garbage is not newer`() {
        assertFalse(UpdateVersionComparator.isNewer("1.0.0", "abc"))
    }

    // --- Empty and blank strings ---

    @Test
    fun `empty strings are equal`() {
        assertEquals(0, UpdateVersionComparator.compare("", ""))
    }

    @Test
    fun `empty current vs real version - real is newer`() {
        assertTrue(UpdateVersionComparator.isNewer("", "1.0.0"))
    }

    @Test
    fun `blank strings are equal`() {
        assertEquals(0, UpdateVersionComparator.compare("   ", "   "))
    }

    @Test
    fun `blank current vs real version - real is newer`() {
        assertTrue(UpdateVersionComparator.isNewer("   ", "1.0.0"))
    }

    // --- Whitespace trimming ---

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertEquals(0, UpdateVersionComparator.compare("  1.0.0  ", "1.0.0"))
    }

    @Test
    fun `whitespace around v prefix is handled`() {
        assertEquals(0, UpdateVersionComparator.compare("  v1.2.3  ", "1.2.3"))
    }
}
