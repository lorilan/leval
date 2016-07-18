package leval.gui.gameScreen

import leval.core.{ActPhase, AttackBeing, Being, Club, Diamond, Heart, Joker, LookCard, MajestyEffect, Move, Origin, RemoveFromHand, Suit}
import leval.gui.gameScreen.being.BeingResourcePane

import scalafx.Includes._
import scalafx.event.subscriptions.Subscription
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.{Alert, ButtonType}
import scalafx.scene.input.MouseEvent

/**
  * Created by lorilan on 7/16/16.
  */
trait ResourceSelector {

  def onClick(brp : BeingResourcePane) : Unit

  def unsuscribeSelector(subscriptions : Iterable[(BeingResourcePane, Subscription)]) =
    {
      println("unsuscribeSelector")
      subscriptions.foreach  {
        case (brp, subscription) =>
          subscription.cancel()
          brp.deactivateHightLight()
      }
    }

  def suscribe
  ( brps : Iterable[BeingResourcePane]
  ) : Iterable[(BeingResourcePane, Subscription)] = {
    println("suscribe")
    brps map {
      brp =>
        println(brp)
        brp.activateHighlight()
        (brp,
          brp.handleEvent(MouseEvent.MouseClicked) {
            me: MouseEvent =>
              onClick(brp)
          })
    }
  }

}

class JokerMindEffectTargetSelector
(val controller: GameScreenControl)
  extends ResourceSelector {
  import controller.pane

  println("JokerMindEffectTargetSelector")

  val (subscriptions, starSubscription) = {
    val opponentSpectres = pane.opponentSpectrePower
    if (opponentSpectres.nonEmpty) {
     println("opponentSpectres.nonEmpty")
      (suscribe(opponentSpectres), None)
    }
    else {
      println("star should be available")
      val brps = controller.pane.targetBeingResource(Club, Seq(controller.opponentId))
      pane.opponentStarPanel.activateHighlight()
      val starSub = pane.opponentStarPanel.handleEvent(MouseEvent.MouseClicked) {
        me: MouseEvent =>
          Seq(MajestyEffect(-1, controller.opponentId),
            RemoveFromHand(Joker.Red),
            ActPhase(Set())) foreach (controller.actor ! )
          unsuscribe()
      }
      (suscribe(brps), Some(starSub))
    }
  }


  def unsuscribe() : Unit = {
    unsuscribeSelector(subscriptions)
    starSubscription.foreach {
      ss =>
        pane.opponentStarPanel.deactivateHightLight()
        ss.cancel
    }
  }


  def onClick(brp: BeingResourcePane): Unit = {
    Seq(AttackBeing(Origin.Hand(Joker.Red),
      brp.being.face, brp.position),
    ActPhase(Set())) foreach (controller.actor ! _)
    unsuscribe()
  }


  def canSelect(b : Being, pos: Suit): Boolean = pos == Club

}

class JokerWeaponEffectTargetSelector
(val controller: GameScreenControl,
 onFinish : () => Unit)
  extends ResourceSelector {
  import controller.pane

  val subscriptions : Either[Iterable[(BeingResourcePane, Subscription)], Subscription] = {
    val brps = controller.pane.targetBeingResource(Heart, Seq(controller.opponentId))
    if (brps.nonEmpty)
     Left(suscribe(brps))
    else Right (pane.opponentStarPanel.handleEvent(MouseEvent.MouseClicked) {
        me: MouseEvent =>
          controller.actor ! MajestyEffect(-1, controller.opponentId)
          controller.actor ! RemoveFromHand(Joker.Black)
          unsuscribeAndFinish()
      })
  }


  def unsuscribeAndFinish() : Unit = {
    subscriptions match {
      case Left(ss) => unsuscribeSelector(ss)
      case Right(ss) =>
        pane.opponentStarPanel.deactivateHightLight()
        ss.cancel
    }
    onFinish()
  }


  def onClick(brp: BeingResourcePane): Unit = {
    controller.actor ! AttackBeing(Origin.Hand(Joker.Black),
      brp.being.face, brp.position)
    unsuscribeAndFinish()
  }


  def canSelect(b : Being, pos: Suit): Boolean = pos == Club

}

class BlackJokerEffect(val controller: GameScreenControl) {
  var playedClub = false
  var playedSpade = false

  val attack = new ButtonType("Attack")
  val collectAndLook = new ButtonType("Collect and look")

  def end() : Unit = controller.actor ! ActPhase(Set())

  val result = new Alert(AlertType.Confirmation) {
    delegate.initOwner(controller.pane.scene().getWindow)
    title = "Attack and collect and look action"
    headerText = s"Choose next effect of action"
    buttonTypes = Seq(attack, collectAndLook)
  }.showAndWait()

  def alertAttack() =
    new Alert(AlertType.Information){
      delegate.initOwner(controller.pane.scene().getWindow)
      title = "Attack Action"
      headerText = "Click on a card or the opponent star to attack"
      //contentText = "Every being has acted"
    }.showAndWait()

  result match {
    case Some(`attack`) =>
      alertAttack()
      new JokerWeaponEffectTargetSelector(controller,
        () => new DrawAndLookAction(controller, 1, 1, controller.canCollectFromRiver,
          end ).apply()
      )
    case Some(`collectAndLook`) =>

      new DrawAndLookAction(controller, 1, 1, controller.canCollectFromRiver,
        () => {alertAttack()
          new JokerWeaponEffectTargetSelector(controller,
          end )}).apply()


  }
}