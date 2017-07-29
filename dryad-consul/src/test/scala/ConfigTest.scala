import java.util.UUID

import io.growing.dryad.ConfigSystem
import io.growing.dryad.client.ConsulClient
import org.scalatest._

@Ignore class ConfigTest extends FunSuite {

  test("Consul client") {
    val configSystem = ConfigSystem()
    val group = configSystem.group
    val namespace = configSystem.namespace
    val path = Seq(namespace, group, "application.conf").mkString("/")
    ConsulClient.kvClient.putValue(
      path,
      """
        {
          name: "Andy.Ai"
          age: 18
        }
      """.stripMargin)
    val config = configSystem.get[ApplicationConfig]
    assertResult(18)(config.age)
    assertResult("Andy.Ai")(config.name)

    for (i ← 1 to 10) {
      val name = UUID.randomUUID().toString
      ConsulClient.kvClient.putValue(
        path,
        s"""
        {
          name: "$name"
          age: $i
        }
        """.stripMargin)

      Thread.sleep(20)
      assertResult(i)(config.age)
      assertResult(name)(config.name)
    }

    ConsulClient.kvClient.deleteKey(path)
  }

  test("Consul client2") {
    val configSystem = ConfigSystem()
    val group = configSystem.group
    val namespace = configSystem.namespace
    val path = Seq(namespace, group, "application.conf").mkString("/")
    ConsulClient.kvClient.putValue(
      path,
      """
        {
          name: "Andy.Ai"
          age: 18
        }
      """.stripMargin)
    val config = configSystem.get[ApplicationConfig]
    assertResult(18)(config.age)
    assertResult("Andy.Ai")(config.name)

    Thread.sleep(10000)

    for (i ← 1 to 100000000) {
      println(s"name: ${config.name}, age: ${config.age}")
      Thread.sleep(1000)
    }

    ConsulClient.kvClient.deleteKey(path)
  }

  test("Consul client3") {
    val configSystem = ConfigSystem()
    val namespace = configSystem.namespace
    val path = Seq(namespace, "application.conf").mkString("/")
    ConsulClient.kvClient.putValue(
      path,
      """
        {
          name: "Andy.Ai"
          age: 18
        }
      """.stripMargin)
    val config = configSystem.get[ApplicationConfig2]
    assertResult(18)(config.age)
    assertResult("Andy.Ai")(config.name)

    for (i ← 1 to 10) {
      val name = UUID.randomUUID().toString
      ConsulClient.kvClient.putValue(
        path,
        s"""
        {
          name: "$name"
          age: $i
        }
        """.stripMargin)

      Thread.sleep(20)

      assertResult(i)(config.age)
      assertResult(name)(config.name)
    }

    ConsulClient.kvClient.deleteKey(path)
  }
}
