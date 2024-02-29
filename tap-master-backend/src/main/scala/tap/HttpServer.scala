package tap

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.{ipv4, port}
import fs2.{Pipe, Stream}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.server.{Router, Server}
import org.http4s.websocket.WebSocketFrame
import org.http4s.{HttpApp, HttpRoutes, Response}
import org.typelevel.log4cats.syntax.*
import org.typelevel.log4cats.{Logger, LoggerFactory}

import scala.concurrent.duration.*

object HttpServer:

  def build(engine: GameEngine, loggerFactory: LoggerFactory[IO], runtime: IORuntime): Resource[IO, Server] =
    Resource
      .eval(loggerFactory.create)
      .flatMap(logger => new HttpServer(engine)(loggerFactory, logger, runtime).http)

class HttpServer(engine: GameEngine)(implicit loggerFactory: LoggerFactory[IO], logger: Logger[IO], runtime: IORuntime):

  private def http: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHttpWebSocketApp(corsApp)
      .withIdleTimeout(30.minutes)
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8081")
      .build

  private def corsApp(ws: WebSocketBuilder[IO]): HttpApp[IO] =
      val app = Router("/api" -> routes(ws)).orNotFound
      CORS.policy
        .withAllowOriginAll(app)
        .unsafeRunSync()

  private def routes(ws: WebSocketBuilder[IO]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "restart" =>
        for
          started <- engine.restartGame
          response <- started match
            case Some(s1: GameState.Started) =>
              for
                _ <- info"Starting game..."
                _ <- info"Red Team:"
                _ <- s1.redTeam.map(_.name).traverse(logger.info(_))
                _ <- info"Blue Team:"
                _ <- s1.blueTeam.map(_.name).traverse(logger.info(_))
                r1 <- Ok(s1.asJson)
              yield r1
            case None =>
              for
                _ <- info"Game already started!"
                r1 <- Ok("Game already started!")
              yield r1
        yield response

      case GET -> Root / "reset" =>
        for
          _ <- logger.info("Reseting game...")
          state <- engine.resetGame
          response <- Ok(state.asJson)
        yield response

      case GET -> Root / "clean-reset" =>
        for
          _ <- logger.info("Reseting game clean...")
          state <- engine.resetClean
          response <- Ok(state.asJson)
        yield response

      case GET -> Root / "state" =>
        engine.getState.flatMap(state => Ok(state.asJson))

      case GET -> Root / "ws" / playerName =>
        for
          _ <- logger.info(s"Registering $playerName...")
          registrationOpt <- engine.registerPlayer(playerName)
          response <- registrationOpt match
            case Some(registration) =>
              buildRegistrationWS(ws, registration)
            case None =>
              BadRequest("Game already started.")
        yield response
    }

  private def buildRegistrationWS(ws: WebSocketBuilder[IO], registration: PlayerRegistration): IO[Response[IO]] =
    val send: Stream[IO, WebSocketFrame] =
      registration.updates.map(state => WebSocketFrame.Text(state.asJson.toString))
    val parseAction: Pipe[IO, WebSocketFrame, GameAction] =
      _.evalMap {
          case WebSocketFrame.Text(text, _) =>
            IO.pure(GameAction.fromString(text))
          case WebSocketFrame.Close(_) =>
            logger.info(s"Removing ${registration.player.name}...") *> engine.removePlayer(registration.player.playerId).as(None)
          case _ =>
            IO.pure(None)
        }
        .collect { case Some(action) => action }
        .evalTap(action => logger.info(s"$action by ${registration.player.name}"))
    val receive: Pipe[IO, WebSocketFrame, Unit] =
      parseAction andThen registration.actions
    ws.build(send, receive)
