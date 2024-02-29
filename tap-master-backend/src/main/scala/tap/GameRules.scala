package tap

import java.util.UUID
import scala.util.Random

case class Color(r: Int, g: Int, b: Int)

case class Player(playerId: UUID, name: String)

enum Team:
  case Blue, Red

enum GameAction:
  case TapNumber, TapString, TapColor

object GameAction:
  def fromString(str: String): Option[GameAction] =
    str match
      case "tap-number" => Some(TapNumber)
      case "tap-string" => Some(TapString)
      case "tap-color" => Some(TapColor)
      case _ => None

case class PlayerAction(playerId: UUID, action: GameAction)

enum GameState:
  case NotStarted(registeredPlayers: List[Player])
  case Started(blueTeam: List[Player], redTeam: List[Player], numbers: Int, strings: String, colors: Color)
  case Finished(winner: Team, blueTeam: List[Player], redTeam: List[Player])

object GameRules:

  private def playerTeam(playerId: UUID, state: GameState.Started): Team | "Unknown" =
    if state.blueTeam.exists(_.playerId == playerId) then Team.Blue
    else if state.redTeam.exists(_.playerId == playerId) then Team.Red
    else "Unknown"

  private def isWinConditionMet(state: GameState.Started): Option[Team] =
    if state.numbers == 30 then Some(Team.Blue)
    else if state.numbers == -30 then Some(Team.Red)
    else if state.strings.contains("b") && state.strings.length == 10 then Some(Team.Blue)
    else if state.strings.contains("r") && state.strings.length == 10 then Some(Team.Red)
    else if state.colors.r == 255 && state.colors.g == 0 && state.colors.b == 0 then Some(Team.Red)
    else if state.colors.r == 0 && state.colors.g == 0 && state.colors.b == 255 then Some(Team.Blue)
    else None

  def registerPlayer(s1: GameState.NotStarted, playerName: String): (GameState.NotStarted, Option[Player]) =
    if (s1.registeredPlayers.exists(player => player.name == playerName)) then (s1, None)
    else 
      val player = Player(UUID.randomUUID(), playerName)
      (s1.copy(registeredPlayers = s1.registeredPlayers :+ player), Some(player))

  def removePlayer(state: GameState, playerId: UUID): GameState =
    state match
      case s1: GameState.NotStarted =>
        s1.copy(registeredPlayers = s1.registeredPlayers.filterNot(_.playerId == playerId))
      case s1: GameState.Started =>
        s1.copy(blueTeam = s1.blueTeam.filterNot(_.playerId == playerId), redTeam = s1.redTeam.filterNot(_.playerId == playerId))
      case s1: GameState.Finished =>
        s1.copy(blueTeam = s1.blueTeam.filterNot(_.playerId == playerId), redTeam = s1.redTeam.filterNot(_.playerId == playerId))

  def startGame(state: GameState.NotStarted): GameState.Started =
    Random.setSeed(System.currentTimeMillis())
    val (blueTeam, redTeam) = Random.shuffle(state.registeredPlayers).splitAt(state.registeredPlayers.length / 2)
    GameState.Started(blueTeam, redTeam, 0, "", Color(0, 0, 0))

  def tap(action: PlayerAction, s1: GameState.Started): GameState.Started | GameState.Finished =
    val s2 = (action.action, playerTeam(action.playerId, s1)) match
      case (GameAction.TapNumber, Team.Blue) =>
        s1.copy(numbers = s1.numbers + 1)
      case (GameAction.TapString, Team.Blue) if s1.strings.contains("r") =>
        s1.copy(strings = s1.strings.drop(1))
      case (GameAction.TapString, Team.Blue) =>
        s1.copy(strings = s1.strings + "b")
      case (GameAction.TapColor, Team.Blue) =>
        s1.copy(colors = Color(Math.max(0, s1.colors.r - 20), s1.colors.g, Math.min(255, s1.colors.b + 5)))
      case (GameAction.TapNumber, Team.Red) =>
        s1.copy(numbers = s1.numbers - 1)
      case (GameAction.TapString, Team.Red) if s1.strings.contains("b") =>
        s1.copy(strings = s1.strings.drop(1))
      case (GameAction.TapString, Team.Red) =>
        s1.copy(strings = s1.strings + "r")
      case (GameAction.TapColor, Team.Red) =>
        s1.copy(colors = Color(Math.min(255, s1.colors.r + 20), s1.colors.g, Math.max(0, s1.colors.b - 5)))
      case _ =>
        println(s"Critical error Unknown team for player ${action.playerId}")
        s1
    isWinConditionMet(s2) match
      case Some(team) => GameState.Finished(team, s1.blueTeam, s1.redTeam)
      case None => s2