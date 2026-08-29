package com.example.campusmate

import com.example.campusmate.domain.llm.LlmHttpUtils
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class LlmHttpUtilsTest {
    @Test
    fun validateHttpsEndpoint_acceptsHttpsHost() {
        assertNull(LlmHttpUtils.validateHttpsEndpoint(" https://api.example.com/v1/ "))
    }

    @Test
    fun validateHttpsEndpoint_rejectsInsecureOrInvalidValues() {
        assertNotNull(LlmHttpUtils.validateHttpsEndpoint("http://api.example.com/v1"))
        assertNotNull(LlmHttpUtils.validateHttpsEndpoint("api.example.com/v1"))
        assertNotNull(LlmHttpUtils.validateHttpsEndpoint("https:///v1"))
        assertNotNull(LlmHttpUtils.validateHttpsEndpoint("   "))
    }
}
