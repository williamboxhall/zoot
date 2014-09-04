package net.fwbrasil.zoot.core

import net.fwbrasil.zoot.core.mapper.JacksonStringMapper
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import net.fwbrasil.zoot.core.request.RequestMethod
import net.fwbrasil.zoot.core.request.Request
import net.fwbrasil.zoot.core.response.Response
import net.fwbrasil.zoot.core.response.ResponseStatus
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import net.fwbrasil.zoot.core.response.ExceptionResponse
import net.fwbrasil.zoot.core.response.NormalResponse

class ClientSpec extends Spec {

    implicit val mirror = scala.reflect.runtime.currentMirror
    implicit val mapper = new JacksonStringMapper

    "should dispatch requests" - {
        "without parameters" in {
            val client =
                Client[TestApi] { request =>
                    request shouldBe Request("/endpoint1/", headers = Map("Content-Type" -> mapper.contentType, "Host" -> "undefined"))
                    Future.successful(Response("\"a\"".getBytes))
                }
            await(client.endpoint1) shouldBe "a"
        }
        "with path param" in {
            val client =
                Client[TestApi] { request =>
                    request shouldBe Request("/12/endpoint2/", method = RequestMethod.POST, params = Map("pathValue" -> "12"), headers = Map("Content-Type" -> mapper.contentType, "Host" -> "undefined"))
                    Future.successful(Response("34".getBytes))
                }
            await(client.endpoint2(12)) shouldBe 34
        }
        "with non-ok response" in {
            val status = ResponseStatus.NOT_FOUND
            val description = "some resource not found"
            val client =
                Client[TestApi] { _ =>
                    Future.successful(Response(description.getBytes, status))
                }
            val exception =
                intercept[ExceptionResponse] {
                    await(client.endpoint1)
                }
            exception.status shouldBe status
            new String(exception.body) shouldBe description
        }

        "with option return" - {
            "None for not found" in {
                val client =
                    Client[TestApi] { _ =>
                        Future.successful(Response(body = Array(), status = ResponseStatus.NOT_FOUND))
                    }
                await(client.endpoint5) shouldBe None
            }
            "Some for ok" in {
                val client =
                    Client[TestApi] { _ =>
                        Future.successful(Response("1".getBytes))
                    }
                await(client.endpoint5) shouldBe Some(1)
            }
        }

        "with response return" - {

            "Response[String]" - {

                def test(response: Response[Array[Byte]]) = {
                    val client =
                        Client[TestApi] { request =>
                            Future.successful(response)
                        }
                    val actual = await(client.endpoint3)
                    actual.body.toList shouldBe response.body.toList
                    actual.status shouldBe response.status
                }

                "ok" in test(Response(body = Array()))
                "nok" in test(Response(body = Array(), status = ResponseStatus.BAD_GATEWAY))
            }

            "Response[CaseClass]" - {
                def test(response: NormalResponse[Any]) = {
                    val client =
                        Client[TestApi] { request =>
                            Future.successful(response.copy(body = mapper.toString(response.body).getBytes))
                        }
                    await(client.endpoint4) shouldBe response
                }

                "ok" in {
                    test(NormalResponse(CaseClass(1, "s")))
                }
                "nok" in {
                    test(NormalResponse(CaseClass(3, "b"), ResponseStatus.BAD_GATEWAY))
                }
            }
        }
    }
}

case class CaseClass(int: Int, string: String)

trait TestApi extends Api {
    @endpoint(method = RequestMethod.GET, path = "/endpoint1/")
    def endpoint1: Future[String]

    @endpoint(method = RequestMethod.POST, path = "/:pathValue/endpoint2/")
    def endpoint2(pathValue: Int): Future[Int]

    @endpoint(method = RequestMethod.GET, path = "/endpoint3/")
    def endpoint3: Future[Response[String]]

    @endpoint(method = RequestMethod.GET, path = "/endpoint4")
    def endpoint4: Future[Response[CaseClass]]

    @endpoint(method = RequestMethod.GET, path = "/endpoint5")
    def endpoint5: Future[Option[Int]]
}