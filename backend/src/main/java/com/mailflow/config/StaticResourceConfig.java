package com.mailflow.config;

import com.mailflow.service.FileStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Replaces `app.use('/uploads', express.static(path.join(__dirname, 'uploads')))`
 * from server.js — serves stored attachments/avatars back over HTTP.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private final FileStorageService fileStorageService;

    public StaticResourceConfig(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        fileStorageService.ensureDirs();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + fileStorageService.uploadsPath() + "/");
    }
}
