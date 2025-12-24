package sh.apptrail.controlplane

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ControlplaneApplication

fun main(args: Array<String>) {
  runApplication<ControlplaneApplication>(*args)
}
