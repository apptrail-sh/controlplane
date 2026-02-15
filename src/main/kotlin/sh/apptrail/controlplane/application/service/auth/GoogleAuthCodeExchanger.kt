package sh.apptrail.controlplane.application.service.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import sh.apptrail.controlplane.infrastructure.config.AuthProperties
import java.util.Base64

@Service
@ConditionalOnProperty(prefix = "apptrail.auth", name = ["enabled"], havingValue = "true")
class GoogleAuthCodeExchanger(
  private val authProperties: AuthProperties,
) {

  private val log = LoggerFactory.getLogger(GoogleAuthCodeExchanger::class.java)
  private val restClient = RestClient.create()
  private val objectMapper = ObjectMapper()

  fun exchangeAuthCode(code: String): GoogleClaims {
    val formBody = listOf(
      "code" to code,
      "client_id" to authProperties.google.clientId,
      "client_secret" to authProperties.google.clientSecret,
      "redirect_uri" to "postmessage",
      "grant_type" to "authorization_code",
    ).joinToString("&") { (k, v) ->
      "${java.net.URLEncoder.encode(k, Charsets.UTF_8)}=${java.net.URLEncoder.encode(v, Charsets.UTF_8)}"
    }

    val response = restClient.post()
      .uri("https://oauth2.googleapis.com/token")
      .header("Content-Type", "application/x-www-form-urlencoded")
      .body(formBody)
      .retrieve()
      .body(Map::class.java)
      ?: throw AuthException("Failed to exchange authorization code")

    val idToken = response["id_token"] as? String
      ?: throw AuthException("No id_token in token response")

    return decodeIdToken(idToken)
  }

  private fun decodeIdToken(idToken: String): GoogleClaims {
    val parts = idToken.split(".")
    if (parts.size != 3) {
      throw AuthException("Invalid ID token format")
    }

    val payload = String(Base64.getUrlDecoder().decode(parts[1]))
    val claims = objectMapper.readValue(payload, Map::class.java)

    val emailVerified = claims["email_verified"]?.toString()?.toBoolean() ?: false
    if (!emailVerified) {
      throw AuthException("Email not verified")
    }

    return GoogleClaims(
      sub = claims["sub"] as? String ?: throw AuthException("Missing sub claim"),
      email = claims["email"] as? String ?: throw AuthException("Missing email claim"),
      name = claims["name"] as? String,
      picture = claims["picture"] as? String,
      emailVerified = emailVerified,
    )
  }
}
