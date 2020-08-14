package controllers

import javax.inject.Inject

import scala.concurrent.{ ExecutionContext, Future }

import play.api.Logger
import play.api.mvc.{ AbstractController, ControllerComponents }
import play.api.libs.json._

// Reactive Mongo imports
import reactivemongo.api.Cursor

import play.modules.reactivemongo.{ // ReactiveMongo Play2 plugin
  MongoController,
  ReactiveMongoApi,
  ReactiveMongoComponents
}

// BSON-JSON conversions/collection
import reactivemongo.play.json._, collection._

/*
 * Example using ReactiveMongo + Play JSON library.
 *
 * There are two approaches demonstrated in this controller:
 * - using JsObjects directly
 * - using case classes that can be turned into JSON using Reads and Writes.
 *
 * This controller uses case classes and their associated Reads/Writes
 * to read or write JSON structures.
 *
 * Instead of using the default Collection implementation (which interacts with
 * BSON structures + BSONReader/BSONWriter), we use a specialized
 * implementation that works with JsObject + Reads/Writes.
 *
 * Of course, you can still use the default Collection implementation
 * (BSONCollection.) See ReactiveMongo examples to learn how to use it.
 */
class MyController @Inject() (
  components: ControllerComponents,
  val reactiveMongoApi: ReactiveMongoApi
) extends AbstractController(components)
  with MongoController with ReactiveMongoComponents {

  implicit def ec: ExecutionContext = components.executionContext

  /*
   * Get a JSONCollection (a Collection implementation that is designed to work
   * with JsObject, Reads and Writes.)
   * Note that the `collection` is not a `val`, but a `def`. We do _not_ store
   * the collection reference to avoid potential problems in development with
   * Play hot-reloading.
   */
  def collection: Future[JSONCollection] = database.map(
    _.collection[JSONCollection]("users"))

  // ------------------------------------------ //
  // Using case classes + JSON Writes and Reads //
  // ------------------------------------------ //
  import models._
  import models.JsonFormats._

  def create = Action.async {
    val user = User(29, "John", "Smith", List(
      Feed("Slashdot news", "http://slashdot.org/slashdot.rdf")))

    // insert the user
    val futureResult = collection.flatMap(_.insert.one(user))

    // when the insert is performed, send a OK 200 result
    futureResult.map(_ => Ok)
  }

  def findByName(lastName: String) = Action.async {
    // let's do our query
    val cursor: Future[Cursor[User]] = collection.map {
      // find all people with name `name`
      _.find(Json.obj("lastName" -> lastName)).
        // sort them by creation date
        sort(Json.obj("created" -> -1)).
        // perform the query and get a cursor of JsObject
        cursor[User]()
    }

    // gather all the JsObjects in a list
    val futureUsersList: Future[List[User]] =
      cursor.flatMap(_.collect[List](-1, Cursor.FailOnError[List[User]]()))

    // everything's ok! Let's reply with the array
    futureUsersList.map { persons =>
      Ok(persons.toString)
    }
  }
}