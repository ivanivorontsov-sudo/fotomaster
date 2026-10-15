package org.ivanivorontsov.fotomaster

import org.junit.Assert.assertTrue

fun assertNear(expected: Int, actual: Int, tol: Int) = assertTrue("expected $expected±$tol but was $actual", kotlin.math.abs(expected - actual) <= tol)
