package sh.apptrail.controlplane.infrastructure.config

import org.eclipse.jetty.server.Handler
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.gzip.GzipHandler
import org.springframework.boot.jetty.JettyServerCustomizer
import org.springframework.boot.jetty.servlet.JettyServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configures Jetty to decompress gzip-compressed request bodies.
 *
 * The agent compresses batch event payloads (>10KB) with gzip and sends them
 * with Content-Encoding: gzip header. This configuration enables Jetty's
 * GzipHandler to automatically decompress such requests.
 *
 * @see <a href="https://github.com/spring-projects/spring-boot/issues/14311">Spring Boot Issue #14311</a>
 */
@Configuration
class JettyConfig {

  @Bean
  fun jettyCustomizer(): WebServerFactoryCustomizer<JettyServletWebServerFactory> {
    return WebServerFactoryCustomizer { factory ->
      factory.addServerCustomizers(GzipInflateCustomizer())
    }
  }

  private class GzipInflateCustomizer : JettyServerCustomizer {
    override fun customize(server: Server) {
      configureGzipHandler(server.handler, INFLATE_BUFFER_SIZE)
    }

    private fun configureGzipHandler(handler: Handler?, inflateBufferSize: Int) {
      when (handler) {
        null -> return
        is GzipHandler -> handler.inflateBufferSize = inflateBufferSize
        is Handler.Wrapper -> configureGzipHandler(handler.handler, inflateBufferSize)
      }
    }
  }

  companion object {
    private const val INFLATE_BUFFER_SIZE = 65536
  }
}
