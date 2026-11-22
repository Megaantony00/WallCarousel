// SPDX-License-Identifier: GPL-3.0-or-later
package com.wallcarousel.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
  @Test
  fun newerPatchIsNewer() {
    assertTrue(isNewerVersion("1.12.1", "1.12"))
  }

  @Test
  fun sameVersionIsNotNewer() {
    assertFalse(isNewerVersion("1.12", "1.12"))
  }

  @Test
  fun testSuffixDoesNotBeatTheSameRelease() {
    assertFalse(isNewerVersion("1.12", "1.12-test"))
    assertTrue(isNewerVersion("1.12.1", "1.12-test"))
  }

  @Test
  fun vPrefixIsIgnored() {
    assertTrue(isNewerVersion("v1.13", "1.12"))
  }

  @Test
  fun majorAndMinorCompareNumerically() {
    assertTrue(isNewerVersion("2.0", "1.99"))
    assertTrue(isNewerVersion("1.10", "1.9"))
    assertFalse(isNewerVersion("1.9", "1.10"))
  }

  @Test
  fun malformedPartsFallBackToZero() {
    assertFalse(isNewerVersion("1.x", "1.0"))
    assertTrue(isNewerVersion("1.x.1", "1.0"))
  }
}
