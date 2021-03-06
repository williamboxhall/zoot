package net.fwbrasil.zoot.core.endpoint

import java.net.URLDecoder

import scala.concurrent.Future

import net.fwbrasil.smirror.SMethod
import net.fwbrasil.smirror.SParameter
import net.fwbrasil.zoot.core.Api
import net.fwbrasil.zoot.core.Encoder
import net.fwbrasil.zoot.core.mapper.StringMapper
import net.fwbrasil.zoot.core.request.Request
import net.fwbrasil.zoot.core.response.ExceptionResponse
import net.fwbrasil.zoot.core.response.ResponseStatus
import net.fwbrasil.zoot.core.util.RichIterable.RichIterable

case class RequestConsumer[A <: Api](endpoint: Endpoint[A], encoders: List[Encoder[Any]]) {

    import endpoint._

    private val encodersByClass =
        encoders.map(e => e.cls -> e).toMap

    def consumeRequest(request: Request, instance: A, mapper: StringMapper) =
        template.tryParse(request).map { pathParams =>
            val parameters = values(pathParams, request, instance, mapper)
            try instance.exec(request, sMethod.asInstanceOf[SMethod[Any]], parameters.toSeq: _*)
            catch {
                case e: Throwable =>
                    Future.failed(e)
            }
        }

    private def values(pathParams: Map[String, String], request: Request, instance: A, mapper: StringMapper) = {
        def getParam(name: String) =
            pathParams.get(name)
                .orElse(request.params.get(name))
        verifyMissingParams(paramValues(request, getParam, instance, mapper))
    }

    private def verifyMissingParams(values: Iterable[(SParameter[A], Option[Any])]) = {
        values.collect {
            case (param, None) if (!param.isOption) => param
        }.ifNonEmpty { missing =>
            throw ExceptionResponse(s"Missing parameters $missing to call $sMethod.", ResponseStatus.BAD_REQUEST)
        }
        values.collect {
            case (param, None) if (param.isOption) => None
            case (param, Some(value)) => value
        }
    }

    private def paramValues(request: Request, getParam: String => Option[String], instance: A, mapper: StringMapper) =
        parameters.zipWith { param =>
            encodersByClass.get(param.sClass.javaClassOption.get.asInstanceOf[Class[Any]]) match {
                case Some(encoder) =>
                    Some(encoder.decode(request))
                case other =>
                    getParam(param.name)
                        .map(URLDecoder.decode)
                        .map(readParam(param, _, mapper))
                        .orElse(param.defaultValueMethodOption.map(_.invoke(instance)))
            }
        }

    private def readParam(param: SParameter[A], value: String, mapper: StringMapper) =
        try mapper.fromString(value)(param.typeTag)
        catch {
            case e: Exception =>
                throw ExceptionResponse(s"Invalid value $value for parameter $param.", ResponseStatus.BAD_REQUEST)
        }

    private def parameters =
        sMethod.parameters.asInstanceOf[List[SParameter[A]]]
}
