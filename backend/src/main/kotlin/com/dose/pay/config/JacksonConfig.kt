package com.dose.pay.config

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.databind.module.SimpleModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 严格字符串反序列化。
 *
 * 原 pydantic 模型里 `amount: str` 遇到 JSON 数字 `100` 会报 string_type → 422；
 * 而 Jackson 默认会把数字宽容地转成字符串，导致契约被破坏（金额 100 被静默接受）。
 * 这里显式关闭该宽容行为，并把原始字面量编进异常消息（`RAW_INPUT=<值>`），
 * 便于全局异常处理器还原 errors[].input。
 */
class StrictStringDeserializer : StdScalarDeserializer<String>(String::class.java) {

    companion object {
        const val RAW_INPUT_MARKER = "RAW_INPUT="
    }

    override fun deserialize(parser: JsonParser, context: DeserializationContext): String {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            val raw = runCatching { parser.text }.getOrNull()
            val kind = parser.currentToken()?.name ?: "UNKNOWN"
            throw MismatchedInputException.from(
                parser,
                String::class.java,
                "Input should be a valid string ($RAW_INPUT_MARKER$raw; kind=$kind)",
            )
        }
        return parser.text
    }
}

@Configuration
class JacksonConfig {

    @Bean
    fun strictStringModule(): SimpleModule {
        val module = SimpleModule("strict-string")
        module.addDeserializer(String::class.java, StrictStringDeserializer())
        return module
    }
}
