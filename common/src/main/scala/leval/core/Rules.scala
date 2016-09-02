package leval.core

/**
  * Created by lorilan on 6/21/16.
  */

import Game.SeqOps
import leval.core.Joker.Black

trait Rules {

  def value(c : Card) : Int // Variante de sinnlos. As = 11

  def startingMajesty : Int

  val losingMajesty : Int = 0
  val winningMajesty : Int = 100

  val maxPlayer : Int = 2

  //way to specific
  //we could generalize that with a (situation, being)=> effect kind of function
  //situation = onDraw/onDeath/onKill/onAttack // ??
  //effect = majestyEffect (target = Origin Star, Targeted Star), draw, retrieve killed face,
  //but then how to handle contextual effect like fool *first* collect of helios ?


  //def onCollect(g : Game, b : Being) : (Game, Option[PlayerInput]) = (g, None)
  def wizardCollect : Int
  def foolFirstCollect : Int

  def drawAndLookValues(origin : Origin) : (Int, Int) =
    origin match {
      case CardOrigin.Hand(_, Card(King, _)) => (1, 3)
      case CardOrigin.Hand(_, Card(Queen, _)) => (1, 2)
      case CardOrigin.Hand(_, _) => (1, 1)
      case co @ CardOrigin.Being(b, _) =>
        (b, co.card) match {
          case (Formation(Fool), Card(Jack, _)) =>
            if(b.firstDraw)
              (foolFirstCollect + 1, 3)
            else
              (3, 3)
          case (Formation(Fool), _) =>
            if(b.firstDraw) (foolFirstCollect, 1)
            else (2, 1)
          case (Formation(Wizard), _)
            if co.suit == Diamond => (wizardCollect, 0) // draw on kill
          case (_, Card(Jack, _)) => (2, 2)
          case _ => (1, 1)
        }
      case Origin.Star(_) => (1, 0)
    }

  def isButcher(o : CardOrigin) : Boolean =
    (o, o.card) match {
      case (CardOrigin.Being(_, _), Card(Jack, Spade)) => true
      case _ => false
    }

  // return (game, being after attack)
  def onAttack
  (g : Game,
   attacker : CardOrigin,
   attacked : Being
  ) : Game = //attack self -5 points
  if(attacker.owner == attacked.owner){
    val malus = if(attacked.lover) 10
    else 5
    g.copy(stars = g.stars.set(attacked.owner, _ - malus))
  }
  else g


  //when the dark lady draw a card to the river, she collect two cards instead of one
  //but if she can draw an additional card thanks to an Eminence Grise,
  //even in the river she will draw only a third card
  def numCardDrawPerAction
  ( origin: Origin,
    target: CollectTarget,
    remainingDrawAction : Int) : Int = {
    (origin, target) match {
      case (CardOrigin.Being(Spectre(BlackLady), _), DeathRiver)
        if remainingDrawAction == 2 => 2
      case _ => 1
    }
  }

  def removeArcanumFromBeing
  (g : Game,
   sAttacker : Option[CardOrigin],
   attacked : Being,
   targetedSuit : Suit) : Game = {
    val removedArcana = attacked resources targetedSuit
    val newBeing = attacked - targetedSuit

    val g1 = newBeing match {
      case Formation(Spectre) => g.setStar(attacked.owner, _ - 5) + newBeing
      case _ => g + newBeing
    }

    (Game.goesToRiver(removedArcana), sAttacker exists isButcher) match {
      case (false, _) => g1
      case (_, false) => g1.copy(deathRiver = removedArcana :: g.deathRiver)
      case (true, true) =>
        removedArcana match {
          case c @ (Card( King | Queen | Jack , _) | Joker(_)) =>
            g1.setStar(sAttacker.get.owner, _ + removedArcana)
          case _ =>
            g1.copy(deathRiver = removedArcana :: g.deathRiver)
        }
    }
  }


  // return (game, card to burry, number of card the killer can draw)
  def onDeath
  (g : Game,
   killer : CardOrigin,
   killed : Being,
   targetedSuit : Suit
  ) : (Game, Set[Card], Int) = {
    val g2 = childAndDauphinEffect(g, killer, killed)
    val g3 = spectreEffectOnDeath(g2, killed)
    val (g4, toBurry) = butcherEffect(g3, killer, killed)
    val removedCard = killed.resources(targetedSuit)
    (g4, toBurry - removedCard, wizardOrEminenceGrise(killer))
  }


  def butcherEffect
  (g : Game,
   killer : CardOrigin,
   killed : Being ) : (Game, Set[Card]) =
    if(isButcher(killer)){
      val f : Card => Boolean = {
        case c @ (Card( King | Queen | Jack , _) | Joker(_)) =>
          Game.goesToRiver(c)
        case _ => false
      }
      val (kept, toBury) = killed.cards.toSet partition f
      println("river = " + g.deathRiver)
      println(s"butcher effect : kept = $kept, toBury = $toBury")
      (g.setStar(killer.owner, _ ++ kept), toBury)
    }
    else (g, killed.cards.toSet)


  def wizardOrEminenceGrise(killer : CardOrigin) : Int =
    (killer, killer.card) match {
      case (CardOrigin.Being(Formation(Wizard), _), Card(Jack, _)) => 2
      case (CardOrigin.Being(Formation(Wizard), _), _)
           | (CardOrigin.Being(_, Diamond), Card(Jack, _)) => 1
      case _ => 0
    }

  def spectreEffectOnDeath( g : Game, killed : Being ) : Game = {
    killed match {
      case Formation(Spectre) => g.setStar(killed.owner, _ + 5)
      case _ => g
    }
  }

  def childAndDauphinEffect
  (g : Game,
   killer : CardOrigin,
   killed : Being
  ) : Game = {
    val childMalus = killed match {
      case Formation(Child) => 5
      case _ => 0
    }
    val dauphinMalus = killed.resources get Heart match {
      case Some(Card(Jack, Heart)) => 5
      case _ => 0
    }
    g.setStar(killer.owner, _  - (childMalus + dauphinMalus))
  }



  def shadowAllowed : Boolean

  //default = 2 players
  //winner, loser
  def result(g : Game) : Option[(PlayerId, PlayerId)] =
  if(g.source.isEmpty) None
  else {
    val someWinner = g.stars.zipWithIndex.find {
      case (s, i) => s.majesty == winningMajesty
    }
    val result = someWinner map {
      case (s, i) => (s.id, g.stars(i+1%2).id)
    }
    if(result.nonEmpty) result
    else {
      val someLoser = g.stars.zipWithIndex.find {
        case (s, i) => s.majesty == losingMajesty
      }
      someLoser map {
        case (s, i) => (g.stars(i+1%2).id, s.id)
      }
    }
  }


  def ended(g : Game) : Boolean =
    g.source.isEmpty || g.stars.exists(s =>
      s.majesty == losingMajesty ||
        s.majesty == winningMajesty)


  def canSoulBeSold : Boolean //variante du diable
  //variante Janus à 4 joueur
  //variante Nédémone, narrative
  //variante O'Stein, draft en début de partie

  //utiliser un décorateur pour implanter les variantes ?


  def legalLoverFormationAtCreation(c : Formation) : Boolean

  def otherLover : PartialFunction[Rank, Rank] = {
    case King => Queen
    case Queen => King
  }

  def checkLegalLover(face : Card, heart : Card) : Boolean =
    (face, heart) match {
      case (Card(fr @ (King | Queen), fs), Card(hr, hs)) =>
        hr == otherLover(fr) && fs == hs
      case _ => false
    }


  //during being creation, we may not have a face
  def validResource(sFace : Option[Card], c : Card, pos : Suit) : Boolean =
    validResource(sFace getOrElse J(0, Black), c, pos)
  //joker as face is a restrictive default : using King or Queen could authorize lovers or "hommes lige"


  def validResource(face : Card, c : Card, pos : Suit) : Boolean

  def validResources(b : Being) : Boolean =  b.resources forall {
    case (Heart, c : C ) if b.lover => checkLegalLover(b.face, c)
    case (pos, c) => validResource(b.face, c, pos)
  }

  def validBeing(b: Being): Boolean = b match {
    case Formation(Shadow) if shadowAllowed => validResources(b)
    case Formation(f) => validResources(b)
    case _ => false
  }


}

trait SinnlosAntaresCommon {
  self : Rules =>

  def value(c: Card): Int = Card.value(c)

  val startingMajesty : Int = 25

  val wizardCollect : Int = 1

  val foolFirstCollect : Int = 2

  val canSoulBeSold : Boolean = false

  val shadowAllowed : Boolean = false


}

object Sinnlos
  extends Rules
    with SinnlosAntaresCommon
    with Serializable {

  override val toString = "Sinnlos"
  def legalLoverFormationAtCreation(c : Formation) : Boolean =
    c == Accomplished

  def validResource(face : Card, c : Card, pos : Suit) = c match {
    case Card(Numeric(_), `pos`) => true
    case _ => false
  }

}

trait AntaresHeliosCommon extends Rules {

  def legalLoverFormationAtCreation(c : Formation) : Boolean = true

  override def checkLegalLover(face : Card, heart : Card) : Boolean =
    super.checkLegalLover(face, heart) || {
      (face, heart) match {
        case (Card(fr@(King | Queen), Heart), Card(Jack, Heart)) => true
        case _ => false
      }
    }

  def validResource(face : Card, c : Card, pos : Suit) = (c, face) match {
    case (Card(Numeric(_), `pos`) | Joker(_), _)
         | (Card(Jack, `pos`), Card(King|Queen, `pos`)) => true
    case _ => false
  }
}

object Antares
  extends AntaresHeliosCommon
    with SinnlosAntaresCommon
    with Serializable{
  override val toString = "Antarès"
}

object Helios
  extends AntaresHeliosCommon
    with Serializable {

  override val toString = "Hélios"

  def value(c: Card): Int = Card.value(c)

  val startingMajesty : Int = 36

  val wizardCollect : Int = 2

  val foolFirstCollect : Int = 3

  val canSoulBeSold : Boolean = false

  val shadowAllowed : Boolean = true

}