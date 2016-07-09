package leval.gui.text

/**
  * Created by lorilan on 7/8/16.
  */
trait ValText {

  val influence_phase : String
  val act_phase : String
  val source_phase : String

  val round : String
  val majesty : String

  val end_of_act_phase : String
  val every_being_has_acted : String

  val twilight_ceremony : String
}

object Fr extends ValText {


  val influence_phase : String = "Phase d'influence"
  val act_phase : String = "Phase des actes"
  val source_phase : String = "Phase de la source"

  val round : String = "Tour"
  val majesty : String = "Majesté"

  val twilight_ceremony : String = "Cérémonie du crépuscule"

  val end_of_act_phase : String = "Fin de la phase des actes"
  val every_being_has_acted : String = "Tous les êtres ont agits"
}

object Eng extends ValText {


  val influence_phase : String = "Influence phase"
  val act_phase : String = "Acts phase"
  val source_phase : String = "Source phase"

  val round : String = "Round"
  val majesty : String = "Majesty"

  val twilight_ceremony : String = "Twilight ceremony"

  val end_of_act_phase : String = "Fin de la phase des actes"
  val every_being_has_acted : String = "Tous les êtres ont agi"
}