package leval.gui.gameScreen

import javafx.geometry.Bounds

import leval.ignore
import leval.core._
import leval.gui.CardImg

import scalafx.geometry.Point2D
import scalafx.scene.Node
import scalafx.scene.image.ImageView
import scalafx.scene.input.MouseEvent

/**
  * Created by lorilan on 6/25/16.
  */

object Origin {
  case object Hand extends Origin
  case class BeingPane(b : Being, suit : Suit) extends Origin
  case object CreateBeingPane extends Origin
}
sealed abstract class Origin


object CardDragAndDrop {

  implicit class NodeOps(val n : Node) extends AnyVal {
    def boundsInScene : Bounds =
      n.localToScene(n.boundsInLocal.value)
  }
}

import leval.gui.gameScreen.CardDragAndDrop.NodeOps
class CardDragAndDrop
( control: GameScreenControl,
  canDragAndDrop : () => Boolean,
  c : Card, origin : Origin)
( val cardImageView : ImageView = CardImg(c))
  extends (MouseEvent => Unit) {

  import control.pane

  var anchorPt: Point2D = null
  var previousLocation: Point2D = null


  def updateCoord(me : MouseEvent) : Unit = {
    cardImageView.x = me.sceneX - (CardImg.width / 2)
    cardImageView.y = me.sceneY - (CardImg.height / 5)
  }


  def apply(me : MouseEvent) : Unit = me.eventType match {
    case MouseEvent.MousePressed if canDragAndDrop() =>
      pane.doHightlightTargets(origin, c)
      anchorPt = new Point2D(me.sceneX, me.sceneY)
      cardImageView.managed = false
      pane.children.add(cardImageView)
      updateCoord(me)

    case MouseEvent.MouseReleased
      if cardImageView != null=>

      val cardBounds = cardImageView.boundsInScene

      //println("[CARD] " + cardImageView.boundsInScene)
      pane.highlightedTargets.find {
        tgt =>
          //  println("[TARGET] " + tgt.boundsInScene)
          tgt.boundsInScene intersects cardBounds
      } foreach { _.onDrop(c, origin)}
      //println()

      pane.unHightlightTargets()
      ignore(pane.children.remove(cardImageView))


    case MouseEvent.MouseDragged
      if cardImageView != null=>
      if (anchorPt != null) {
        previousLocation = anchorPt
        anchorPt = new Point2D(me.sceneX, me.sceneY)

        val tx = anchorPt.x - previousLocation.x
        val ty = anchorPt.y - previousLocation.y

        cardImageView.x.value = cardImageView.x.value + tx
        cardImageView.y.value = cardImageView.y.value + ty

      }
    case _ => ()
  }
}
