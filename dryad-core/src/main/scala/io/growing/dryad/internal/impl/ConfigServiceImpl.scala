package io.growing.dryad.internal.impl

import java.util.concurrent.atomic.AtomicReference

import com.google.common.cache.CacheBuilder
import com.typesafe.config.{ Config, ConfigFactory, ConfigRef }
import io.growing.dryad.annotation.Configuration
import io.growing.dryad.internal.{ ConfigService, ConfigurationDesc }
import io.growing.dryad.parser.ConfigParser
import io.growing.dryad.provider.ConfigProvider
import io.growing.dryad.snapshot.LocalFileConfigSnapshot
import net.sf.cglib.proxy.Enhancer

import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }

/**
 * Component:
 * Description:
 * Date: 16/3/26
 *
 * @author Andy Ai
 */
class ConfigServiceImpl(provider: ConfigProvider) extends ConfigService {

  private[this] val separator = "/"
  private[this] val objects = CacheBuilder.newBuilder().build[String, AnyRef]()
  private[this] val configs = CacheBuilder.newBuilder().build[String, Config]()

  override def get[T: ClassTag](namespace: String, group: String): T = {
    val clazz = implicitly[ClassTag[T]].runtimeClass
    objects.get(clazz.getName, () ⇒ createObjectRef(clazz, namespace, group)).asInstanceOf[T]
  }

  override def get(name: String, namespace: String, group: Option[String]): Config = {
    val path = getPath(name, namespace, group)
    configs.get(path, () ⇒ {
      val refreshAndFlush = (configuration: ConfigurationDesc, ref: AtomicReference[Config]) ⇒ {
        ref.set(ConfigFactory.parseString(configuration.payload))
        LocalFileConfigSnapshot.flash(configuration)
      }
      val underlying: AtomicReference[Config] = new AtomicReference[Config]()
      val c = provider.load(path, (configuration: ConfigurationDesc) ⇒ {
        refreshAndFlush(configuration, underlying)
      })
      refreshAndFlush(c, underlying)
      new ConfigRef(underlying)
    })
  }

  override def getConfigAsString(name: String, namespace: String, group: Option[String]): String = {
    provider.load(getPath(name, namespace, group)).payload
  }

  override def getConfigAsStringRecursive(name: String, namespace: String, group: String): String = {
    var result: Try[ConfigurationDesc] = null
    Seq(namespace, group, name).inits.exists {
      case Nil ⇒
        println("nil")
        false
      case segments ⇒
        result = Try(provider.load(segments.mkString(separator)))
        result.isSuccess
    }
    result match {
      case Success(config) ⇒ config.payload
      case Failure(t)      ⇒ throw t
    }
  }

  private[this] def createObjectRef(clazz: Class[_], namespace: String, group: String): AnyRef = {
    val refreshAndFlush = (configuration: ConfigurationDesc, ref: ObjectRef, parser: ConfigParser[_]) ⇒ {
      val config = ConfigFactory.parseString(configuration.payload)
      ref.reference.set(parser.parse(config))
      LocalFileConfigSnapshot.flash(configuration)
    }
    val annotation = clazz.getAnnotation(classOf[Configuration])
    val ref: ObjectRef = new ObjectRef(new AtomicReference[Any]())
    val parser = annotation.parser().newInstance()
    val path = getPath(annotation.name(), namespace, if (annotation.ignoreGroup()) None else Option(group))
    val configuration = provider.load(path, (configuration: ConfigurationDesc) ⇒
      refreshAndFlush(configuration, ref, parser))
    refreshAndFlush(configuration, ref, parser)

    val enhancer = new Enhancer()
    enhancer.setSuperclass(clazz)
    enhancer.setCallbackType(classOf[ObjectRef])
    enhancer.setCallback(ref)
    val parameterTypes: Array[Class[_]] = clazz.getConstructors.head.getParameterTypes
    val refObj = enhancer.create(parameterTypes, parameterTypes.map { c ⇒
      val v = c.getName match {
        case "byte"    ⇒ 0
        case "short"   ⇒ 0
        case "int"     ⇒ 0
        case "long"    ⇒ 0L
        case "float"   ⇒ 0.0f
        case "double"  ⇒ 0.0d
        case "char"    ⇒ '\u0000'
        case "boolean" ⇒ false
        case _         ⇒ null
      }
      v.asInstanceOf[AnyRef]
    })
    refObj
  }

  def getPath(name: String, namespace: String, group: Option[String] = None): String = {
    val paths = group.fold(Seq(namespace, name))(_group ⇒ Seq(namespace, _group, name))
    paths.filterNot(_.trim.isEmpty).mkString(separator)
  }

}

