package tap

import cats.effect.{ExitCode, IO, IOApp, Resource}
import org.typelevel.log4cats.*
import org.typelevel.log4cats.slf4j.Slf4jFactory

object Main extends IOApp:

  val loggerFactory: LoggerFactory[IO] =
    Slf4jFactory.create[IO]

  def run(args: List[String]): IO[ExitCode] =
    (for
      logger <- Resource.eval(loggerFactory.create)
      _ <- Resource.eval(logger.info("Starting server"))
      engine <- Resource.eval(GameEngine.build)
      server <- HttpServer.build(engine, loggerFactory, runtime)
      _ <- Resource.eval(engine.runGame)
    yield ExitCode.Success).useForever
