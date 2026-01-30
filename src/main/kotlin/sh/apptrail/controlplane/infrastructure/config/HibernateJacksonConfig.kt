package sh.apptrail.controlplane.infrastructure.config

import org.hibernate.cfg.MappingSettings
import org.hibernate.type.format.AbstractJsonFormatMapper
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper
import java.lang.reflect.Type

@Configuration
class HibernateJacksonConfig {

    @Bean
    fun hibernateJacksonCustomizer(jsonMapper: JsonMapper): HibernatePropertiesCustomizer {
        return HibernatePropertiesCustomizer { properties ->
            properties[MappingSettings.JSON_FORMAT_MAPPER] = Jackson3FormatMapper(jsonMapper)
        }
    }
}

/**
 * Custom FormatMapper for Hibernate 7 that uses Jackson 3's JsonMapper.
 * Hibernate 7.2 doesn't have built-in Jackson 3 support, so we implement
 * AbstractJsonFormatMapper to bridge the gap.
 */
class Jackson3FormatMapper(private val jsonMapper: JsonMapper) : AbstractJsonFormatMapper() {

    override fun <T : Any?> fromString(value: CharSequence, type: Type): T {
        return jsonMapper.readValue(value.toString(), jsonMapper.constructType(type))
    }

    override fun <T : Any?> toString(value: T, type: Type): String {
        return jsonMapper.writeValueAsString(value)
    }
}
