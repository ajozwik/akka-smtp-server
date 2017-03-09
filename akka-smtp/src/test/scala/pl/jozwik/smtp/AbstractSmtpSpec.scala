/*
 * Copyright (c) 2017 Andrzej Jozwik
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package pl.jozwik.smtp

import java.net.InetAddress
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterAll
import pl.jozwik.smtp.client.ClientActor
import pl.jozwik.smtp.server._
import pl.jozwik.smtp.util.{AbstractAsyncSpec, SocketAddress, TestUtils}

import scala.concurrent.Await
import scala.concurrent.duration._

object ActorSpec {
  private val number = Iterator from 1
}

trait ActorSpec extends StrictLogging {

  import ActorSpec._

  protected implicit val actorSystem = ActorSystem(
    s"test-${number.next()}",
    ConfigFactory.parseResources("application-test.conf")
  )

  private val TIMEOUT = 3000
  protected implicit val timeout = Timeout(TIMEOUT, TimeUnit.MILLISECONDS)

}

trait AbstractActorSpec extends AbstractAsyncSpec with BeforeAndAfterAll with ActorSpec {
  override protected def afterAll(): Unit = {
    val terminated = Await.result(actorSystem.terminate(), timeout.duration)
    logger.debug(s"$terminated")
  }

  protected final def interceptAndPrint[T <: Throwable](f: => scala.Any)(implicit manifest: scala.reflect.Manifest[T]): T = {
    val t = intercept[T] {
      f
    }
    logger.error(s"$t")
    t
  }
}

trait SmtpSpec extends ActorSpec {

  import TestUtils._

  protected val host = InetAddress.getLocalHost.getHostAddress

  import scala.language.postfixOps

  private val defaultMaxSize = 1024

  protected def readTimeout = 30 seconds

  protected def maxSize = defaultMaxSize

  protected final val configuration = Configuration(notOccupiedPortNumber, maxSize, readTimeout)

  protected def consumerProps = LogConsumerActor.props

  protected def addressHandler: AddressHandler = NopAddressHandler

  protected final val clientRef = actorSystem.actorOf(ClientActor.props(), "ClientActor")
  protected val materializer = ActorMaterializer()
  protected implicit val address = SocketAddress(host, configuration.port)
  protected final val server = StreamServer(consumerProps, configuration, addressHandler)(actorSystem, materializer)
}

trait AbstractSmtpSpec extends AbstractActorSpec with SmtpSpec {

  override protected def beforeAll() = {

  }

  override protected def afterAll() = {
    server.close()
    super.afterAll()

  }

}

