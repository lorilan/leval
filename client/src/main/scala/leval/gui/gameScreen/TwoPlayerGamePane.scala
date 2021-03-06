package leval.gui.gameScreen

/**
  * Created by lorilan on 6/22/16.
  */

import leval.ignore
import leval.core._
import leval.gui.gameScreen.being._

import scala.collection.mutable
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.{Group, Node, Scene}
import scalafx.scene.control.{Button, Label}
import scalafx.scene.image.{ImageView, WritableImage}
import scalafx.scene.input.MouseEvent
import scalafx.scene.layout._
import scalafx.scene.text.TextAlignment

abstract class CardDropTarget extends HighlightableRegion {
  def onDrop(origin : CardOrigin) : Unit
}

class RiverPane
(control : GameScreenControl,
 fitHeight : Double) extends CardDropTarget {
  val content = new HBox()
  decorated = content
  def onDrop(origin: CardOrigin): Unit =
    control.drawAndLook(origin)

  def river = control.game.deathRiver

  private def images : Seq[CardImageView] =
    if(river.isEmpty) Seq[CardImageView]()
    else
      river.tail.foldLeft(List(CardImg(river.head, Some(fitHeight)))) {
        case (acc, c) =>
          CardImg.cutLeft(c, 3, Some(fitHeight)) :: acc
      }

  def update() : Unit = Platform.runLater {
    content.children.clear()
    content.children = images
  }

}

class TwoPlayerGamePane
( val oGame : ObservableGame,
  val playerGameId : Int,
  val controller : GameScreenControl,
  val widthInit : Double,
  val heightInit : Double)
  extends GridPane {
  pane =>

  val cardHeight = (heightInit / 10).floor
  val cardResizeRatio = cardHeight / CardImg.height

  val cardWidth = CardImg.width * cardResizeRatio

  val riverAreaHeight = cardHeight

  val handAreaHeight = cardHeight //((heightInit - riverAreaHeight - (2 * playerAreaHeight))/2).floor

  val playerAreaHeight = ((heightInit - riverAreaHeight - 2 * handAreaHeight) / 2).floor
  //(0.325 * heightInit).ceil



  //= ~ 0.15 * heightInit


  val leftColumnInfo = new ColumnConstraints(0.1 * widthInit)
  val gameAreaInfo = new ColumnConstraints(0.9 * widthInit)


  Seq(new RowConstraints(handAreaHeight),
    new RowConstraints(playerAreaHeight),
    new RowConstraints(riverAreaHeight),
    new RowConstraints(playerAreaHeight),
    new RowConstraints(handAreaHeight)) foreach (rowConstraints.add(_))

  columnConstraints add leftColumnInfo
  columnConstraints add gameAreaInfo

  prefWidth = widthInit
  prefHeight = heightInit

  maxWidth = widthInit
  maxHeight = heightInit

  minWidth = widthInit
  minHeight = heightInit

  import controller.{opponentId, texts}
  import oGame.{game => _, _}

  style = "-fx-background-color: white"

  def player = stars(playerGameId)

  def opponentSpectrePower : Iterable[BeingResourcePane] =
    beingPanes(opponentId) filter {bp =>
      val Formation(f) = bp.being
      //println(bp.being.face + " : " + f)
      bp.being match {
        case Formation(Spectre) => true
        case _ => false
      }} map (_.resourcePane(Club).get)

  def targetBeingResource(s: Suit, sides : Seq[Int]) : Iterable[BeingResourcePane]  = {
    val bps = sides match {
      case Seq(id) => beingPanes(id)
      case _ => beingPanes
    }
    bps.flatMap(_.resourcePane(s))
  }


  private [this] var highlightableRegions = Seq[CardDropTarget]()
  def highlightedTargets = highlightableRegions
  def doHightlightTargets(origin : CardOrigin): Unit = {
    val highlighteds =
      if(createBeeingPane.isOpen) createBeeingPane.targets(origin.card)
      else if(educateBeingPane.isOpen) origin.card match {
        case c : C => educateBeingPane.targets(c)
        case j : J =>
          val resources = educateBeingPane.being.resources
          educateBeingPane.targets(j) filter {
            t => (resources get t.pos).isEmpty
          }
      }
      else {
        val highlighteds0 : Seq[CardDropTarget] =
          Target(oGame.game, origin.card) flatMap {
            case SelfStar => Seq(playerStarPanel)
            case OpponentStar => Seq(opponentStarPanel)
            case Source => Seq(deck)
            case DeathRiver => Seq(riverPane)
            case OpponentSpectrePower =>
              opponentSpectrePower
            case TargetBeingResource(s, sides) =>
              targetBeingResource(s,sides)
            case _ => Seq()
          }
        origin match{
          case CardOrigin.Hand(_, _) =>
            createBeeingPane.createBeingLabel +: highlighteds0
          case _ => highlighteds0
        }
      }
    highlighteds foreach (_.activateHighlight())
    highlightableRegions = highlighteds
  }


  def unHightlightTargets(): Unit = {
    val hed = highlightableRegions
    highlightableRegions = Seq()
    hed.foreach(_.deactivateHightLight())
  }

  val opponentStarPanel = StarPanel(controller,
    leftColumnInfo.prefWidth(), opponentId)
  val playerStarPanel = StarPanel(controller,
    leftColumnInfo.prefWidth(), playerGameId)

  val deck = new CardDropTarget {

    def onDrop(origin: CardOrigin): Unit =
      controller.drawAndLook(origin)

    //style = "-fx-border-width: 1; -fx-border-color: black;"

    val numCardTxt = new Label(""){
      style =
        "-fx-font-size: 24pt;" +
          "-fx-background-color: white;"
      textAlignment = TextAlignment.Center
    }

    def numCardImage = {
      val scene = new Scene(new Group(numCardTxt))
      val img = new WritableImage(50,40)
      scene.snapshot(img)
      img
    }
    val numCardImageView =
      new ImageView {
        preserveRatio = true
        alignmentInParent = Pos.Center
        fitWidth = cardWidth / 2
        visible = false
      }

    children = Seq(CardImg.back(Some(cardHeight)), numCardImageView, highlight)

    handleEvent(MouseEvent.MouseEntered) {
      me : MouseEvent =>
        numCardTxt.text = oGame.source.size.toString
        numCardImageView.image = numCardImage
        numCardImageView.visible = true
    }

    handleEvent(MouseEvent.MouseExited) {
      me : MouseEvent =>
        numCardImageView.visible = false

    }

  }

  val riverPane = new RiverPane(controller, cardHeight)

  val handPane = new PlayerHandPane(controller, handAreaHeight)

  val createBeeingPane =
    new CreateBeingPane(controller,
      handPane,
      cardWidth, cardHeight)

  val endPhaseButton =
    new Button(texts.do_end_phase){
      onMouseClicked = {
        me : MouseEvent =>
          if(createBeeingPane.isOpen)
            createBeeingPane.menuMode()

          controller.endPhase()

      }
      visible = controller.isCurrentPlayer
    }
  val handPaneWrapper = new BorderPane {
    center = handPane
    right = endPhaseButton
  }


  val educateBeingPane =
    new EducateBeingPane(controller,
      handPane,
      cardWidth, cardHeight)

  val opponentHandPane = new OpponnentHandPane(controller, handAreaHeight)
  //style = "-fx-border-width: 1; -fx-border-color: black;"
  val playerAreasStyle = s"-fx-hgap : ${(cardWidth / 10).ceil} ;"+
    s"-fx-hgap : ${(cardHeight / 10).ceil} ;"

  val opponentBeingsPane = new FlowPane(){
    style = playerAreasStyle
  }

  val playerBeingsPane = new FlowPane(){
    style = playerAreasStyle
  }
  def beingsPane(o : Orientation) = o match {
    case Player => playerBeingsPane
    case Opponent => opponentBeingsPane
  }

  private [gameScreen] val beingPanesMap = mutable.Map[Card, BeingPane]()

  controller.game beingsOwnBy controller.opponentId foreach addOpponentBeingPane
  controller.game beingsOwnBy controller.playerGameIdx foreach addPlayerBeingPane


  def beingPanes : Iterable[BeingPane] = beingPanesMap.values

  def resourcesPanes : Iterable[BeingResourcePane] =
    beingPanes flatMap (_.resourcePanes)

  def beingsPane(playerId : Int) : Pane =
    if(playerGameId == playerId) playerBeingsPane
    else opponentBeingsPane

  def beingPanes(sideId : Int) : Iterable[BeingPane] =
    beingPanesMap.values filter (_.being.owner == sideId)

  def addOpponentBeingPane(b : Being) : Unit = {
    val bp = new BeingPane(controller, b.face, cardWidth, cardHeight, Opponent)
    beingPanesMap += (b.face -> bp)
    leval.ignore(opponentBeingsPane.children add bp)
  }

  def addPlayerBeingPane(b : Being) : Unit = {
    val bp = new BeingPane(controller, b.face, cardWidth, cardHeight, Player)
    beingPanesMap += (b.face -> bp)
    ignore(playerBeingsPane.children add bp)
  }

  val playerArea = new BorderPane() {
    //style = "-fx-border-width: 1; -fx-border-color: black;"
    center = playerBeingsPane
    right = createBeeingPane
  }

  padding = Insets.Empty

  val statusPane =
    new StatusPane(controller.game, handAreaHeight)

  val leftColumn = Seq(
    statusPane,
    opponentStarPanel,
    deck,
    playerStarPanel
  )

  leftColumn.zipWithIndex.foreach {
    case (area, index) =>
      GridPane.setConstraints(area, 0, index)

  }

  val gameAreas: List[Node] =
    List(opponentHandPane,
      opponentBeingsPane, riverPane,
      playerArea, handPaneWrapper)


  gameAreas.zipWithIndex.foreach {
    case (area, index) => GridPane.setConstraints(area, 1, index)
  }

  children = leftColumn ++: gameAreas

}

