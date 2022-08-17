package io.kafkamate

import zio.ZIO
import zio.test.*
import zio.test.Assertion.equalTo
import zio.test.ZIOSpecDefault

/**
 * This is a sample test for the CI; TODO delete after we start creating real tests for the app
 */
object BasicSpec extends ZIOSpecDefault {

  override def spec =
    suite("Simple test")(
      test("Check zio result") {
        assertTrue(1 == 1)
      }
    )
}
