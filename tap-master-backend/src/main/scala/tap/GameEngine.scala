package tap

import cats.effect.{IO, Ref}
import fs2.concurrent.Topic
import fs2.{Pipe, Stream}

import java.util.UUID

type PlayerActions = Pipe[IO, GameAction, Unit]

type PlayerUpdates = Stream[IO, GameState]

case class PlayerRegistration(player: Player, actions: PlayerActions, updates: PlayerUpdates)

object GameEngine:

  def build: IO[GameEngine] =
    val newGame = GameState.NotStarted(List.empty)
    for
      state <- Ref.of[IO, GameState](newGame)
      actions <- Topic[IO, PlayerAction]
      updates <- Topic[IO, GameState]
    yield GameEngine(state, actions, updates)

class GameEngine(
             state: Ref[IO, GameState],
             actions: Topic[IO, PlayerAction],
             updates: Topic[IO, GameState]
           ):
  
  def getState: IO[GameState] =
    state.get

  def registerPlayer(playerName: String): IO[Option[PlayerRegistration]] =
    state.modify {
      case s1: GameState.NotStarted =>
        val (s2, playerOpt) =
          GameRules.registerPlayer(s1, playerName)
        playerOpt match
          case Some(player) =>
            val playerActionsPipe: Pipe[IO, GameAction, PlayerAction] =
              _.map(PlayerAction(player.playerId, _))
            val playerActions: PlayerActions =
              playerActionsPipe andThen actions.publish
            val playerUpdates: PlayerUpdates =
              updates.subscribe(5)
            val registration =
              PlayerRegistration(player, playerActions, playerUpdates)
            (s2, Some((s2, registration)))
          case None =>
            (s2, None)
      case s1 =>
        (s1, None)
    }.flatMap {
      case Some((s2, registration)) =>
        updates.publish1(s2).as(Some(registration))
      case None =>
        IO.pure(None)
    }

  def removePlayer(playerId: UUID): IO[Unit] =
    state.update(GameRules.removePlayer(_, playerId))

  def startGame: IO[Option[GameState.Started]] =
    state.modify {
      case s1: GameState.NotStarted =>
        val startedGame = GameRules.startGame(s1)
        (startedGame, Some(startedGame))
      case s1 =>
        (s1, None)
    }.flatTap {
      case Some(s2) =>
        updates.publish1(s2).void
      case None =>
        IO.unit
    }

  def resetGame: IO[GameState] =
    state.updateAndGet {
      case s1: GameState.Started =>
        GameState.NotStarted(s1.redTeam ++ s1.blueTeam)
      case s1: GameState.Finished =>
        GameState.NotStarted(s1.redTeam ++ s1.blueTeam)
      case s1 => s1
    }.flatTap(updates.publish1)

  def resetClean: IO[GameState] =
    state.updateAndGet(_ => GameState.NotStarted(List.empty))

  def restartGame: IO[Option[GameState.Started]] =
    resetGame *> startGame

  def runGame: IO[Unit] =
    val gameStateStream: Stream[IO, GameState] = for
      action <- actions.subscribe(5)
      s2 <- Stream.eval(state.updateAndGet {
        case started: GameState.Started =>
          GameRules.tap(action, started)
        case s1 => s1
      })
    yield s2
    val updatesStream = updates.publish(gameStateStream)
    updatesStream.compile.drain
