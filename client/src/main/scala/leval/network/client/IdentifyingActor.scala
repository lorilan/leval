package leval
package network
package client

import akka.actor._
import scala.concurrent.duration._


object IdentifyingActor {
  def props(netHandle : NetWorkController,
            serverPath : String) =
    Props(new IdentifyingActor(netHandle, serverPath)).withDispatcher("javafx-dispatcher")
}
class IdentifyingActor private
( netHandle : NetWorkController,
  val serverPath : String)
  extends Actor {



  def sendIdentifyRequest() : Unit = {
    context.actorSelection(serverPath) ! Identify(serverPath)
    import context.dispatcher
    val _ = context.system.scheduler.scheduleOnce(3.seconds, self, ReceiveTimeout)
  }

  sendIdentifyRequest()

  def receive = identifying

  def identifying: Actor.Receive = {
    case ActorIdentity(`serverPath`, Some(server)) =>
      println("In liaison with server")
      //context.watch(server)

      val menuProps =
        MenuActor.props(server, netHandle)
          .withDispatcher("javafx-dispatcher")
      netHandle.actor = context.actorOf(menuProps)
      context become passive
      //context.become(active(server))


    case ActorIdentity(`serverPath`, None) => println(s"Server not available: $serverPath")
    case ReceiveTimeout              => sendIdentifyRequest()
    case _                           => println("Not ready yet")
  }


  def passive : Actor.Receive = {
    case _ => ()
  }

  def active(server: ActorRef): Actor.Receive = {
    case Disconnect =>
      print("Unwatching server ... ")
      context.unwatch(server)
      println("unwatching done")

    case Terminated(`server`) =>
      println("Server terminated")
      sendIdentifyRequest()
      context.become(identifying)

    case ReceiveTimeout =>
    // ignore
  }
}





