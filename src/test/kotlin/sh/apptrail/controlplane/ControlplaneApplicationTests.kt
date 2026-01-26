package sh.apptrail.controlplane

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest
@Testcontainers
class ControlplaneApplicationTests {

  companion object {
    @Container
    @ServiceConnection
    @JvmStatic
    val postgres = PostgreSQLContainer("postgres:18-alpine")
  }

  @Test
  fun contextLoads() {
  }

}
