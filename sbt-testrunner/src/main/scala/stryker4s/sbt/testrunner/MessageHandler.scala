package stryker4s.sbt.testrunner

import stryker4s.api.testprocess._
import sbt.testing.Status

trait MessageHandler {
  def handleMessage(req: Request): Response

  def setupState(req: Request): MessageHandler
}

final class CleanMessageHandler() extends MessageHandler {

  def handleMessage(req: Request): Response = {
    SetupTestContextSuccesful()
  }

  def setupState(req: Request): MessageHandler = {
    req match {
      case SetupTestContext(testContext) =>
        val testRunner = new SbtTestRunner(testContext)
        println("Set up testContext")
        new TestRunnerMessageHandler(testRunner)
      case other =>
        throw new IllegalStateException(
          s"CleanMessageHandler cannot handle message. Expected a SetupTestContext, but received $other"
        )

    }
  }

}

final class TestRunnerMessageHandler(testRunner: SbtTestRunner) extends MessageHandler {
  def handleMessage(req: Request): Response =
    req match {
      case StartTestRun(mutation) =>
        val status = testRunner.runMutation(mutation)
        statusToTestResult(status)
      case StartInitialTestRun() =>
        val status = testRunner.initialTestRun()
        statusToTestResult(status)
      case other: SetupTestContext =>
        throw new IllegalStateException(
          s"TestRunnerMessageHandler cannot handle SetupTestContext, received $other"
        )
    }

  def statusToTestResult(status: Status): TestResultResponse =
    status match {
      case Status.Success => TestsSuccessful()
      case _              => TestsUnsuccessful()
    }

  def setupState(req: Request): MessageHandler = this

}