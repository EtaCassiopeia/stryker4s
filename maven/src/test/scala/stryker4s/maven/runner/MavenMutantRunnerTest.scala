package stryker4s.maven.runner

import better.files._
import org.apache.maven.project.MavenProject
import org.apache.maven.shared.invoker._
import org.mockito.captor.ArgCaptor
import org.mockito.scalatest.MockitoSugar
import stryker4s.config.Config
import stryker4s.extension.mutationtype.LesserThan
import stryker4s.model.{Killed, Mutant, Survived}
import stryker4s.mutants.findmutants.SourceCollector
import stryker4s.report.Reporter
import stryker4s.testutil.Stryker4sSuite

import scala.collection.JavaConverters._
import scala.meta._
import java.{util => ju}

class MavenMutantRunnerTest extends Stryker4sSuite with MockitoSugar {
  implicit val config: Config = Config.default
  describe("runInitialTest") {
    it("should succeed on exit-code 0 invoker") {
      val context = mock[MavenTestRunnerContext]
      val invokerMock = mock[Invoker]
      val mockResult = mock[InvocationResult]
      when(mockResult.getExitCode).thenReturn(0)
      when(invokerMock.execute(any)).thenReturn(mockResult)
      val sut = new MavenMutantRunner(new MavenProject(), invokerMock, mock[SourceCollector], mock[Reporter])

      val result = sut.runInitialTest(context)

      verify(invokerMock).setWorkingDirectory(any)
      result should be(true)
    }

    it("should fail on exit-code 1 invoker") {
      val context = mock[MavenTestRunnerContext]
      val invokerMock = mock[Invoker]
      val mockResult = mock[InvocationResult]
      when(mockResult.getExitCode).thenReturn(1)
      when(invokerMock.execute(any)).thenReturn(mockResult)
      val sut = new MavenMutantRunner(new MavenProject(), invokerMock, mock[SourceCollector], mock[Reporter])

      val result = sut.runInitialTest(context)

      verify(invokerMock).setWorkingDirectory(any)
      result should be(false)
    }

    it("should not add the environment variable") {
      val context = mock[MavenTestRunnerContext]
      val invokerMock = mock[Invoker]
      val mockResult = mock[InvocationResult]
      when(mockResult.getExitCode).thenReturn(0)
      when(invokerMock.execute(any)).thenReturn(mockResult)
      val captor = ArgCaptor[InvocationRequest]
      val sut = new MavenMutantRunner(new MavenProject(), invokerMock, mock[SourceCollector], mock[Reporter])

      val result = sut.runInitialTest(context)

      result should be(true)
      verify(invokerMock).setWorkingDirectory(any)
      verify(invokerMock).execute(captor)
      val invokedRequest = captor.value
      invokedRequest.getShellEnvironments should be(empty)
    }
  }

  describe("runMutants") {
    it("should have a Killed mutant on a exit-code 1") {
      val context = mock[MavenTestRunnerContext]
      val invokerMock = mock[Invoker]
      val mockResult = mock[InvocationResult]
      when(mockResult.getExitCode).thenReturn(1)
      when(invokerMock.execute(any)).thenReturn(mockResult)
      val sut = new MavenMutantRunner(new MavenProject(), invokerMock, mock[SourceCollector], mock[Reporter])

      val result = sut.runMutant(Mutant(1, q">", q"<", LesserThan), context)

      verify(invokerMock).setWorkingDirectory(any)
      result shouldBe a[Killed]
    }

    it("should have a Survived mutant on a exit-code 0") {
      val context = mock[MavenTestRunnerContext]
      val invokerMock = mock[Invoker]
      val mockResult = mock[InvocationResult]
      when(mockResult.getExitCode).thenReturn(0)
      when(invokerMock.execute(any)).thenReturn(mockResult)
      val sut = new MavenMutantRunner(new MavenProject(), invokerMock, mock[SourceCollector], mock[Reporter])

      val result = sut.runMutant(Mutant(1, q">", q"<", LesserThan), context)

      verify(invokerMock).setWorkingDirectory(any)
      result shouldBe a[Survived]
    }

    it("should add the environment variable to the request") {
      val context = mock[MavenTestRunnerContext]
      val invokerMock = mock[Invoker]
      val mockResult = mock[InvocationResult]
      when(mockResult.getExitCode).thenReturn(1)
      when(invokerMock.execute(any)).thenReturn(mockResult)
      val captor = ArgCaptor[InvocationRequest]
      val sut = new MavenMutantRunner(new MavenProject(), invokerMock, mock[SourceCollector], mock[Reporter])

      sut.runMutant(Mutant(1, q">", q"<", LesserThan), context)

      verify(invokerMock).setWorkingDirectory(any)
      verify(invokerMock).execute(captor)
      val invokedRequest = captor.value
      invokedRequest.getShellEnvironments.asScala should equal(Map("ACTIVE_MUTATION" -> "1"))
      invokedRequest.getGoals should contain only "test"
      invokedRequest.isBatchMode should be(true)
      invokedRequest.getProperties.getProperty("surefire.skipAfterFailureCount") should equal("1")
      invokedRequest.getProperties.getProperty("test") shouldBe null
    }

    it("should add the test-filter for all test runners") {
      val expectedTestFilter = Seq("*MavenMutantRunnerTest", "*OtherTest")
      implicit val config: Config = Config.default.copy(testFilter = expectedTestFilter)

      val context = mock[MavenTestRunnerContext]
      val invokerMock = mock[Invoker]
      val mockResult = mock[InvocationResult]
      when(mockResult.getExitCode).thenReturn(0)
      when(invokerMock.execute(any)).thenReturn(mockResult)
      val captor = ArgCaptor[InvocationRequest]
      val sut = new MavenMutantRunner(new MavenProject(), invokerMock, mock[SourceCollector], mock[Reporter])

      sut.runMutant(Mutant(1, q">", q"<", LesserThan), context)

      verify(invokerMock).setWorkingDirectory(any)
      verify(invokerMock).execute(captor)
      val invokedRequest = captor.value
      invokedRequest.getProperties.getProperty("test") should equal(expectedTestFilter.mkString(", "))
      invokedRequest.getProperties.getProperty("wildcardSuites") should equal(expectedTestFilter.mkString(","))
    }

    it("should add the test-filter for surefire if a property is already defined") {
      val expectedTestFilter = "*MavenMutantRunnerTest"
      implicit val config: Config = Config.default.copy(testFilter = Seq(expectedTestFilter))

      val context = mock[MavenTestRunnerContext]
      val invokerMock = mock[Invoker]
      val mockResult = mock[InvocationResult]
      when(mockResult.getExitCode).thenReturn(0)
      when(invokerMock.execute(any)).thenReturn(mockResult)
      val captor = ArgCaptor[InvocationRequest]
      val mavenProject = new MavenProject()
      mavenProject.getProperties().setProperty("test", "*OtherTest")
      val sut = new MavenMutantRunner(mavenProject, invokerMock, mock[SourceCollector], mock[Reporter])

      sut.runMutant(Mutant(1, q">", q"<", LesserThan), context)

      verify(invokerMock).setWorkingDirectory(any)
      verify(invokerMock).execute(captor)
      val invokedRequest = captor.value
      invokedRequest.getProperties.getProperty("test") should equal(s"*OtherTest, $expectedTestFilter")
    }

    it("should add the test-filter for scalatest if a property is already defined") {
      val expectedTestFilter = "*MavenMutantRunnerTest"
      implicit val config: Config = Config.default.copy(testFilter = Seq(expectedTestFilter))

      val context = mock[MavenTestRunnerContext]
      val invokerMock = mock[Invoker]
      val mockResult = mock[InvocationResult]
      when(mockResult.getExitCode).thenReturn(0)
      when(invokerMock.execute(any)).thenReturn(mockResult)
      val captor = ArgCaptor[InvocationRequest]
      val mavenProject = new MavenProject()
      mavenProject.getProperties().setProperty("wildcardSuites", "*OtherTest")
      val sut = new MavenMutantRunner(mavenProject, invokerMock, mock[SourceCollector], mock[Reporter])

      sut.runMutant(Mutant(1, q">", q"<", LesserThan), context)

      verify(invokerMock).setWorkingDirectory(any)
      verify(invokerMock).execute(captor)
      val invokedRequest = captor.value
      invokedRequest.getProperties.getProperty("wildcardSuites") should equal(s"*OtherTest,$expectedTestFilter")
    }
  }
}
