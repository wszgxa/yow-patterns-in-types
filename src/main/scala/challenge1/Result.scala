package challenge1

import core._, Syntax._


/*
 * Handling errors without exceptions....
 * ======================================
 */

/*
 * A well-typed set of errors that can occur.
 */
sealed trait Error
case class Explosion(exception: Throwable) extends Error
case object NotFound extends Error
case object InvalidRequest extends Error
case object InvalidMethod extends Error
case object Unauthorized extends Error

object Error {
  implicit def ErrorEqual =
    Equal.derived[Error]
}

/*
 * A result type that represents one of our errors or a success.
 */
case class Fail[A](error: Error) extends Result[A]
case class Ok[A](value: A) extends Result[A]

sealed trait Result[A] {
  /*
   * Exercise 1.1:
   *
   * Implement the catamorphism for Result[A].
   *
   * Hint: Try using pattern matching.
   *
   * scala> Ok(1).fold(_ => 0, x => x)
   *  = 1
   *
   * scala> Fail(NotFound).fold(_ => 0, x => x)
   *  = 0
   */
  def fold[X](
    fail: Error => X,
    ok: A => X
  ): X = this match {
    case Fail(error) => fail(error)
    case Ok(value) => ok(value)
  }

  /*
   * Exercise 1.2:
   *
   * Implement map for Result[A].
   *
   * The following laws must hold:
   *  1) r.map(z => z) == r
   *  2) r.map(z => f(g(z))) == r.map(g).map(f)
   *
   * scala> Ok(1).map(x => x + 10)
   *  = Ok(11)
   *
   * scala> Fail(NotFound).map(x => x + 10)
   *  = Fail(NotFound)
   *
   * Advanced: Try using flatMap.
   */
  def map[B](f: A => B): Result[B] = fold(Result.fail, f andThen Result.ok)


  /*
   * Exercise 1.3:
   *
   * Implement flatMap (a.k.a. bind, a.k.a. >>=).
   *
   * The following law must hold:
   *   r.flatMap(f).flatMap(g) == r.flatMap(z => f(z).flatMap(g))
   *
   * scala> Ok(1).flatMap(x => Ok(x + 10))
   *  = Ok(11)
   *
   * scala> Ok(1).flatMap(x => Fail[Int](Unauthorized))
   *  = Fail(Unauthorized)
   *
   * scala> Fail(NotFound).map(x => Ok(x + 10))
   *  = Fail(NotFound)
   *
   * scala> Fail(NotFound).map(x => Fail(Unauthorized))
   *  = Fail(NotFound)
   *
   * Advanced: Try using fold.
   */
  def flatMap[B](f: A => Result[B]): Result[B] = fold(Result.fail, f)


  /*
   * Exercise 1.4:
   *
   * Extract the value if it is success case otherwise use default value.
   *
   *
   * scala> Ok(1).getOrElse(10)
   *  = 1
   *
   * scala> Fail(NotFound).getOrElse(10)
   *  = 10
   */
  def getOrElse(otherwise: => A): A = fold(_ => otherwise, identity)


  /*
   * Exercise 1.4:
   *
   * Implement choice, take this result if successful otherwise take
   * the alternative.
   *
   * scala> Ok(1) ||| Ok(10)
   *  = Ok(1)
   *
   * scala> Ok(1) ||| Fail[Int](Unauthorized)
   *  = Ok(1)
   *
   * scala> Fail[Int](NotFound) ||| Ok(10)
   *  = Ok(10)
   *
   * scala> Fail[Int](NotFound) ||| Fail[Int](Unauthorized)
   *  = Fail(Unauthorized)
   */
  def |||(alternative: => Result[A]): Result[A] = fold(_ => alternative, Result.ok)
}

object Result {
  def fail[A](error: Error): Result[A] =
    Fail(error)

  def ok[A](value: A): Result[A] =
    Ok(value)

  implicit def ResultMonad: Monad[Result] = new Monad[Result] {
    def point[A](a: => A) = ok(a)
    def bind[A, B](a: Result[A])(f: A => Result[B]) = a flatMap f
  }

  implicit def ResultEqual[A: Equal] =
    Equal.from[Result[A]]((a, b) => a.fold(
      e => b.fold(_ === e, _ => false),
      a => b.fold(_ => false, _ === a)
    ))
}


/*
 * *Challenge* Exercise 1.5: The worlds most trivial HTTP calculator.
 *
 * We are implementing a way to compute a number via a HTTP like
 * API.
 *  - We will send a method which is one of GET|POST|PUT|DELETE.
 *  - We want to send an integer as a request body.
 *  - We will send a path of what calculation to use.
 *
 * Complete the implementation, some of the methods are provided
 * with type signatures to get started.
 */
object Example {

  /** Simplified method data type. */
  sealed trait Method
  case object Get extends Method
  case object Post extends Method
  case object Put extends Method
  case object Delete extends Method

  /*
   * Parse the method if it is valid, otherwise fail with InvalidRequest.
   *
   * Hint: Scala defines String#toInt, but warning it throws exceptions if it is not a valid Int :|
   */
  def request(body: String): Result[Int] =
    try {Result.ok(body.toInt)} catch { case _ : Throwable  => Result.fail(InvalidRequest) }

  /* Parse the method if it is valid, otherwise fail with InvalidMethod. */
  def method(method: String): Result[Method] = method.toLowerCase match {
    case "get" => Result.ok(Get)
    case "post" => Result.ok(Post)
    case "put" => Result.ok(Put)
    case "delete" => Result.ok(Delete)
    case _ => Result.fail(InvalidMethod)
  }

  /*
   * Route method and path to an implementation.
   *
   * A minimal implementation is:
   *   GET /single -> n * 1
   *   GET /double -> n * 2
   *   GET /triple -> n * 3
   *   PUT *       -> Unauthorized
   *   POST *      -> Unauthorized
   *   DELETE *    -> Unauthorized
   *   *           -> NotFound
   */
  def route(method: Method, path: String): Result[Int => Int] = (method, path) match {
    case (Get, "/single") => Result.ok((t:Int) => 1 * t)
    case (Get, "/double") => Result.ok(2 * (_: Int))
    case (Get, "/triple") => Result.ok(3 * (_: Int))
    case (Put, _) | (Post, _) | (Delete, _) => Result.fail(Unauthorized)
    case _ => Result.fail(NotFound)
  }

  /*
   * Attempt to compute an `answer`, by:
   *  - determining method
   *  - selecting implementation
   *  - determing request value
   *  - using the implementation and request value to compute an answer.
   */
  def service(path: String, methodx: String, body: String): Result[Int] = for {
    method <- method(methodx)
    algorithm <- route(method, path)
    request <- request(body)
  } yield algorithm(request)

  /*
   * Sometimes we always an `answer`, so default to 0 if
   * our request failed in any way.
   */
  def run(path: String, method: String, body: String): Int =
    service(path, method, body).getOrElse(0)
}
