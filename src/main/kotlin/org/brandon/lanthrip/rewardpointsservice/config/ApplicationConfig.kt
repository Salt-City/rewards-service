package org.brandon.lanthrip.rewardpointsservice.config

import jakarta.servlet.MultipartConfigElement
import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize

@Configuration
class ApplicationConfig {

    @Bean
    fun multipartConfigElement(): MultipartConfigElement {
        val f = MultipartConfigFactory()
        f.setMaxFileSize(DataSize.ofGigabytes(1))
        return f.createMultipartConfig()
    }
}