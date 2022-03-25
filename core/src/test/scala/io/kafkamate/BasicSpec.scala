package io.kafkamate

import zio.ZIO
import zio.test.*
import zio.test.Assertion.equalTo

/**
 * This is a sample test for the CI; TODO delete after we start creating real tests for the app
 */
object BasicSpec extends DefaultRunnableSpec {

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    suite("Simple test")(
      testM("Check zio result") {
        assertM(ZIO.succeed(1))(equalTo(1))
      }
    )
}
