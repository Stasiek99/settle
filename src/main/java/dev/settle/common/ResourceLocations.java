package dev.settle.common;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
@EnableConfigurationProperties(WebProperties.class)
public class ResourceLocations {

    private final String publicPath;

    public ResourceLocations(WebProperties properties) {
        this.publicPath = properties.publicPath();
    }

    public URI ofChild(Object id) {
        String requestPath = ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
        return UriComponentsBuilder.fromPath(publicPath + (requestPath == null ? "" : requestPath))
                .path("/{id}")
                .buildAndExpand(id)
                .toUri();
    }
}
